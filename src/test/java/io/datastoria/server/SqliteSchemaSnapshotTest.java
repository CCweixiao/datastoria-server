package io.datastoria.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SqliteSchemaSnapshotTest {

  @Autowired DataSource migratedDataSource;

  @Test
  void schemaSnapshotBuildsTheSameTablesAndColumnsAsFlyway() throws Exception {
    try (Connection snapshot = DriverManager.getConnection("jdbc:sqlite::memory:");
        Connection migrated = migratedDataSource.getConnection()) {
      try (var statement = snapshot.createStatement()) {
        statement.execute("PRAGMA foreign_keys=ON");
      }
      ScriptUtils.executeSqlScript(snapshot, new ClassPathResource("db/schema/sqlite/schema.sql"));

      assertThat(columns(snapshot)).isEqualTo(columns(migrated));
    }
  }

  private static Map<String, Set<String>> columns(Connection connection) throws Exception {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    try (ResultSet tables =
        connection
            .getMetaData()
            .getTables(connection.getCatalog(), null, "ds_%", new String[] {"TABLE"})) {
      while (tables.next()) {
        String table = tables.getString("TABLE_NAME");
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet columns =
            connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
          while (columns.next()) {
            names.add(columns.getString("COLUMN_NAME"));
          }
        }
        result.put(table, names);
      }
    }
    return result;
  }
}
