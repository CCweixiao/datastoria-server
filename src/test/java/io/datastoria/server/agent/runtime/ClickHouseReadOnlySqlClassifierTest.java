package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClickHouseReadOnlySqlClassifierTest {

  private final ClickHouseReadOnlySqlClassifier classifier = new ClickHouseReadOnlySqlClassifier();

  @Test
  void acceptsReadOnlyQueriesAndIgnoresKeywordsInsideLiteralsAndComments() {
    for (String sql :
        List.of(
            "SELECT 1",
            "SELECT 'DROP TABLE x; INSERT INTO y' AS text;",
            "WITH rows AS (SELECT 1) SELECT * FROM rows",
            "EXPLAIN indexes = 1 SELECT * FROM system.numbers LIMIT 1",
            "DESCRIBE TABLE system.tables",
            "SHOW TABLES",
            "EXISTS TABLE system.tables",
            "/* DROP TABLE x */ SELECT 1 -- INSERT INTO y\n")) {
      assertThat(classifier.requireReadOnly(sql)).doesNotEndWith(";");
    }
  }

  @Test
  void rejectsMultipleStatementsAndMutationOrControlStatements() {
    for (String sql :
        List.of(
            "SELECT 1; SELECT 2",
            "INSERT INTO x VALUES (1)",
            "ALTER TABLE x DELETE WHERE 1",
            "CREATE TABLE x (id UInt8) ENGINE=Memory",
            "DROP TABLE x",
            "TRUNCATE TABLE x",
            "OPTIMIZE TABLE x FINAL",
            "SYSTEM FLUSH LOGS",
            "KILL QUERY WHERE 1",
            "WITH 1 AS x INSERT INTO t SELECT x")) {
      assertThatThrownBy(() -> classifier.requireReadOnly(sql))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsExternalTableFunctionsOutputAndSafetyOverrides() {
    for (String sql :
        List.of(
            "SELECT * FROM file('/etc/passwd')",
            "SELECT * FROM url('https://example.com', CSV)",
            "SELECT * FROM s3('https://bucket/object')",
            "SELECT * FROM remote('host', system, tables)",
            "SELECT * FROM clusterAllReplicas('analytics', system.tables)",
            "SELECT 1 INTO OUTFILE '/tmp/result'",
            "SELECT 1 FORMAT CSV",
            "SELECT number FROM numbers(10) SETTINGS max_result_rows=0",
            "SELECT 1 SETTINGS readonly=0")) {
      assertThatThrownBy(() -> classifier.requireReadOnly(sql))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void allowsClusterAllReplicasOnlyForConfiguredLiteralCluster() {
    assertThat(
            classifier.requireReadOnly(
                "SELECT hostName() FROM clusterAllReplicas('analytics', system.tables)",
                "analytics"))
        .isEqualTo("SELECT hostName() FROM clusterAllReplicas('analytics', system.tables)");

    for (String sql :
        List.of(
            "SELECT * FROM clusterAllReplicas('other', system.tables)",
            "SELECT * FROM clusterAllReplicas(cluster_name, system.tables)",
            "SELECT * FROM clusterAllReplicas('analytics', system.tables)"
                + " JOIN clusterAllReplicas('other', system.one) USING tuple()")) {
      assertThatThrownBy(() -> classifier.requireReadOnly(sql, "analytics"))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void rejectsBlankAndUnterminatedInput() {
    for (String sql : List.of("", "  ", "SELECT 'unterminated", "SELECT 1 /* open")) {
      assertThatThrownBy(() -> classifier.requireReadOnly(sql))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
