package io.datastoria.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

/**
 * Verifies that the SQLite and MySQL migration trees produce a logically equivalent schema: same
 * tables, same column names, same primary keys, same foreign-key relationships, and same unique
 * constraints. Type names are allowed to differ (TEXT vs varchar, INTEGER vs boolean, etc.) per the
 * dual-dialect type-mapping rules.
 *
 * <p>Requires Docker for the MySQL Testcontainer; skipped automatically if Docker is unavailable.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaParityTest {

  private static MySQLContainer<?> mysql;

  @Autowired DataSource sqliteDataSource;

  @BeforeAll
  static void startMysql() {
    try {
      mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("parity_test");
      mysql.start();
      Flyway.configure()
          .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
          .locations("classpath:db/migration/mysql")
          .load()
          .migrate();
    } catch (Exception e) {
      mysql = null;
      Assumptions.abort("Docker unavailable — skipping MySQL parity test: " + e.getMessage());
    }
  }

  @AfterAll
  static void stopMysql() {
    if (mysql != null) {
      mysql.stop();
    }
  }

  @Test
  void sqliteAndMySQLHaveSameTables() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    try (Connection c =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
      SchemaSnapshot mysqlSnap = snapshot(c);
      assertThat(mysqlSnap.tables()).isEqualTo(sqlite.tables());
    }
  }

  @Test
  void sqliteAndMySQLHaveSameColumns() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    try (Connection c =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
      SchemaSnapshot mysqlSnap = snapshot(c);
      for (String table : sqlite.tables()) {
        assertThat(mysqlSnap.columns().get(table))
            .as("columns for table %s", table)
            .isEqualTo(sqlite.columns().get(table));
      }
    }
  }

  @Test
  void sqliteAndMySQLHaveSamePrimaryKeys() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    try (Connection c =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
      SchemaSnapshot mysqlSnap = snapshot(c);
      for (String table : sqlite.tables()) {
        assertThat(mysqlSnap.pks().get(table))
            .as("PK for table %s", table)
            .isEqualTo(sqlite.pks().get(table));
      }
    }
  }

  @Test
  void sqliteAndMySQLHaveSameForeignKeys() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    try (Connection c =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
      SchemaSnapshot mysqlSnap = snapshot(c);
      for (String table : sqlite.tables()) {
        assertThat(mysqlSnap.fks().get(table))
            .as("FKs for table %s", table)
            .isEqualTo(sqlite.fks().get(table));
      }
    }
  }

  @Test
  void sqliteAndMySQLHaveSameUniqueConstraints() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    try (Connection c =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
      SchemaSnapshot mysqlSnap = snapshot(c);
      for (String table : sqlite.tables()) {
        assertThat(mysqlSnap.uniqueIndexes().get(table))
            .as("unique constraints for table %s", table)
            .isEqualTo(sqlite.uniqueIndexes().get(table));
      }
    }
  }

  // ---- introspection helpers ----

  private static SchemaSnapshot snapshot(DataSource ds) throws SQLException {
    try (Connection c = ds.getConnection()) {
      return snapshot(c);
    }
  }

  private static SchemaSnapshot snapshot(Connection c) throws SQLException {
    DatabaseMetaData md = c.getMetaData();
    String catalog = c.getCatalog();
    String schema = md.getDatabaseProductName().contains("SQLite") ? null : c.getSchema();
    Set<String> tables = new LinkedHashSet<>();
    Map<String, List<String>> columns = new TreeMap<>();
    Map<String, List<String>> pks = new TreeMap<>();
    Map<String, Set<String>> fks = new TreeMap<>();
    Map<String, Set<String>> uniqueIndexes = new TreeMap<>();

    try (ResultSet rs = md.getTables(catalog, schema, "ds_%", new String[] {"TABLE"})) {
      while (rs.next()) {
        tables.add(rs.getString("TABLE_NAME"));
      }
    }
    for (String table : tables) {
      List<String> cols = new ArrayList<>();
      try (ResultSet rs = md.getColumns(catalog, schema, table, null)) {
        while (rs.next()) {
          cols.add(rs.getString("COLUMN_NAME"));
        }
      }
      columns.put(table, cols);

      List<String> pkCols = new ArrayList<>();
      try (ResultSet rs = md.getPrimaryKeys(catalog, schema, table)) {
        while (rs.next()) {
          pkCols.add(rs.getString("COLUMN_NAME"));
        }
      }
      pks.put(table, pkCols);

      Set<String> fkSet = new LinkedHashSet<>();
      try (ResultSet rs = md.getImportedKeys(catalog, schema, table)) {
        while (rs.next()) {
          fkSet.add(
              rs.getString("FKCOLUMN_NAME")
                  + "->"
                  + rs.getString("PKTABLE_NAME")
                  + "."
                  + rs.getString("PKCOLUMN_NAME"));
        }
      }
      fks.put(table, fkSet);

      Map<String, List<String>> uniqueColumns = new TreeMap<>();
      try (ResultSet rs = md.getIndexInfo(catalog, schema, table, true, false)) {
        while (rs.next()) {
          String indexName = rs.getString("INDEX_NAME");
          String columnName = rs.getString("COLUMN_NAME");
          if (indexName != null && columnName != null) {
            uniqueColumns.computeIfAbsent(indexName, ignored -> new ArrayList<>()).add(columnName);
          }
        }
      }
      uniqueIndexes.put(
          table,
          uniqueColumns.values().stream()
              .map(columnsInIndex -> String.join(",", columnsInIndex))
              .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
    }
    return new SchemaSnapshot(tables, columns, pks, fks, uniqueIndexes);
  }

  private record SchemaSnapshot(
      Set<String> tables,
      Map<String, List<String>> columns,
      Map<String, List<String>> pks,
      Map<String, Set<String>> fks,
      Map<String, Set<String>> uniqueIndexes) {}
}
