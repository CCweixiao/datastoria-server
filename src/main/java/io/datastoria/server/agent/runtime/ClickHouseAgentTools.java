package io.datastoria.server.agent.runtime;

import java.util.Map;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.service.ClickHouseConnectionService;

import reactor.core.publisher.Mono;

/** Server-side SQL tool bound to the run's persisted ClickHouse connection. */
public final class ClickHouseAgentTools {

  private final ClickHouseConnectionService service;
  private final String connectionId;
  private final Identity identity;

  public ClickHouseAgentTools(
      ClickHouseConnectionService service, String connectionId, Identity identity) {
    this.service = service;
    this.connectionId = connectionId;
    this.identity = identity;
  }

  @Tool(
      name = "execute_sql",
      description =
          "Execute a ClickHouse SQL statement using the current server-side connection. "
              + "Use read-only SQL unless the user explicitly requests a mutation.",
      readOnly = false)
  public Mono<String> executeSql(
      @ToolParam(name = "sql", required = true, description = "ClickHouse SQL to execute")
          String sql) {
    return service.query(connectionId, sql, Map.of("default_format", "JSON"), identity);
  }

  @Tool(
      name = "get_tables",
      description = "List ClickHouse databases and tables visible through the current connection.",
      readOnly = true)
  public Mono<String> getTables() {
    return service.query(
        connectionId,
        "SELECT database, name, engine FROM system.tables"
            + " WHERE database NOT IN ('system', 'INFORMATION_SCHEMA', 'information_schema')"
            + " ORDER BY database, name",
        Map.of("default_format", "JSON"),
        identity);
  }

  @Tool(
      name = "explore_schema",
      description =
          "Return column names, types, defaults and comments for one ClickHouse database table.",
      readOnly = true)
  public Mono<String> exploreSchema(
      @ToolParam(name = "database", required = true, description = "Database name") String database,
      @ToolParam(name = "table", required = true, description = "Table name") String table) {
    String sql =
        "SELECT name, type, default_kind, default_expression, comment"
            + " FROM system.columns WHERE database = '"
            + literal(database)
            + "' AND table = '"
            + literal(table)
            + "' ORDER BY position";
    return service.query(connectionId, sql, Map.of("default_format", "JSON"), identity);
  }

  @Tool(
      name = "validate_sql",
      description = "Validate ClickHouse SQL syntax without executing the statement.",
      readOnly = true)
  public Mono<String> validateSql(
      @ToolParam(name = "sql", required = true, description = "ClickHouse SQL to validate")
          String sql) {
    return service.query(
        connectionId, "EXPLAIN SYNTAX " + sql, Map.of("default_format", "JSON"), identity);
  }

  @Tool(
      name = "collect_sql_optimization_evidence",
      description =
          "Collect ClickHouse optimizer evidence for one SELECT statement using EXPLAIN indexes.",
      readOnly = true)
  public Mono<String> collectSqlOptimizationEvidence(
      @ToolParam(name = "sql", required = true, description = "SELECT statement to inspect")
          String sql) {
    return service.query(
        connectionId, "EXPLAIN indexes = 1 " + sql, Map.of("default_format", "JSON"), identity);
  }

  @Tool(
      name = "search_query_log",
      description =
          "Search recent ClickHouse query_log entries by case-insensitive query text. "
              + "Returns execution metrics and errors.",
      readOnly = true)
  public Mono<String> searchQueryLog(
      @ToolParam(name = "query_text", description = "Optional text contained in query")
          String queryText,
      @ToolParam(name = "minutes", description = "Lookback window in minutes") Integer minutes,
      @ToolParam(name = "limit", description = "Maximum rows") Integer limit) {
    int safeMinutes = bounded(minutes, 60, 1, 10_080);
    int safeLimit = bounded(limit, 50, 1, 500);
    String predicate =
        queryText == null || queryText.isBlank()
            ? ""
            : " AND positionCaseInsensitive(query, '" + literal(queryText) + "') > 0";
    String sql =
        "SELECT event_time, query_id, user, query_duration_ms, read_rows, read_bytes,"
            + " memory_usage, result_rows, exception, query FROM system.query_log"
            + " WHERE event_time >= now() - INTERVAL "
            + safeMinutes
            + " MINUTE AND type IN ('QueryFinish', 'ExceptionWhileProcessing')"
            + predicate
            + " ORDER BY event_time DESC LIMIT "
            + safeLimit;
    return service.query(connectionId, sql, Map.of("default_format", "JSON"), identity);
  }

  @Tool(
      name = "collect_cluster_status",
      description =
          "Collect current ClickHouse cluster, replica, part, merge and mutation status from "
              + "server-side system tables.",
      readOnly = true)
  public Mono<String> collectClusterStatus() {
    String sql =
        "SELECT"
            + " (SELECT count() FROM system.clusters) AS cluster_nodes,"
            + " (SELECT count() FROM system.replicas WHERE is_readonly OR is_session_expired)"
            + " AS unhealthy_replicas,"
            + " (SELECT count() FROM system.parts WHERE active) AS active_parts,"
            + " (SELECT count() FROM system.merges) AS active_merges,"
            + " (SELECT count() FROM system.mutations WHERE NOT is_done) AS pending_mutations";
    return service.query(connectionId, sql, Map.of("default_format", "JSON"), identity);
  }

  @Tool(
      name = "collect_rca_evidence",
      description =
          "Collect server-side evidence for ClickHouse high part count diagnosis. "
              + "Use database and table together to narrow the investigation.",
      readOnly = true)
  public Mono<String> collectRcaEvidence(
      @ToolParam(name = "symptom", required = true, description = "Currently: high_part_count")
          String symptom,
      @ToolParam(name = "database", description = "Optional database") String database,
      @ToolParam(name = "table", description = "Optional table") String table) {
    if (!"high_part_count".equals(symptom)) {
      return Mono.error(new IllegalArgumentException("Unsupported RCA symptom"));
    }
    String predicate =
        (database == null || database.isBlank())
            ? ""
            : " AND database = '"
                + literal(database)
                + "'"
                + ((table == null || table.isBlank())
                    ? ""
                    : " AND table = '" + literal(table) + "'");
    String sql =
        "SELECT database, table, count() AS active_parts, uniqExact(partition) AS partitions,"
            + " sum(rows) AS rows, sum(bytes_on_disk) AS bytes_on_disk"
            + " FROM system.parts WHERE active"
            + predicate
            + " GROUP BY database, table ORDER BY active_parts DESC LIMIT 100";
    return service.query(connectionId, sql, Map.of("default_format", "JSON"), identity);
  }

  private static String literal(String value) {
    return value == null ? "" : value.replace("'", "''");
  }

  private static int bounded(Integer value, int fallback, int min, int max) {
    int resolved = value == null ? fallback : value;
    return Math.max(min, Math.min(max, resolved));
  }
}
