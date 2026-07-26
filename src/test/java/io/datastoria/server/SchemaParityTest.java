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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/**
 * Verifies that the SQLite and MySQL migration trees produce a logically equivalent schema: same
 * tables, columns, keys, and unique constraints. Type names may differ by dialect.
 *
 * <p>Requires Docker; skipped automatically only if Docker itself is unavailable. Container or
 * migration failures remain hard test failures.
 */
@SpringBootTest
@ActiveProfiles("test")
class SchemaParityTest {

  private static MySQLContainer<?> mysql;

  @Autowired DataSource sqliteDataSource;

  @BeforeAll
  static void startDatabases() throws Exception {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(),
        "Docker unavailable — skipping relational schema parity");
    try {
      mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("parity_test");
      mysql.start();
      Flyway.configure()
          .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
          .locations("classpath:db/migration/mysql")
          .load()
          .migrate();
    } catch (Exception e) {
      if (mysql != null && mysql.isRunning()) {
        mysql.stop();
      }
      mysql = null;
      throw e;
    }
  }

  @AfterAll
  static void stopDatabases() {
    if (mysql != null) {
      mysql.stop();
    }
  }

  @Test
  void allDialectsHaveSameTables() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    for (SchemaSnapshot other : relationalSnapshots()) {
      assertThat(other.tables()).isEqualTo(sqlite.tables());
    }
  }

  @Test
  void allDialectsHaveSameColumns() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    for (SchemaSnapshot other : relationalSnapshots()) {
      for (String table : sqlite.tables()) {
        assertThat(other.columns().get(table))
            .as("columns for table %s", table)
            .isEqualTo(sqlite.columns().get(table));
      }
    }
  }

  @Test
  void allDialectsHaveSamePrimaryKeys() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    for (SchemaSnapshot other : relationalSnapshots()) {
      for (String table : sqlite.tables()) {
        assertThat(other.pks().get(table))
            .as("PK for table %s", table)
            .isEqualTo(sqlite.pks().get(table));
      }
    }
  }

  @Test
  void allDialectsHaveSameForeignKeys() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    for (SchemaSnapshot other : relationalSnapshots()) {
      for (String table : sqlite.tables()) {
        assertThat(other.fks().get(table))
            .as("FKs for table %s", table)
            .isEqualTo(sqlite.fks().get(table));
      }
    }
  }

  @Test
  void allDialectsHaveSameUniqueConstraints() throws SQLException {
    SchemaSnapshot sqlite = snapshot(sqliteDataSource);
    for (SchemaSnapshot other : relationalSnapshots()) {
      for (String table : sqlite.tables()) {
        assertThat(other.uniqueIndexes().get(table))
            .as("unique constraints for table %s", table)
            .isEqualTo(sqlite.uniqueIndexes().get(table));
      }
    }
  }

  private static List<SchemaSnapshot> relationalSnapshots() throws SQLException {
    List<SchemaSnapshot> snapshots = new ArrayList<>();
    try (Connection connection =
        DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
      snapshots.add(snapshot(connection));
    }
    return snapshots;
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
