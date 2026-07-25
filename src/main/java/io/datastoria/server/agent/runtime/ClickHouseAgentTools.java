package io.datastoria.server.agent.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.datastoria.server.api.error.ProviderOperationException;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.service.ClickHouseConnectionService;

import reactor.core.publisher.Mono;

/** Server-side SQL tool bound to the run's persisted ClickHouse connection. */
public final class ClickHouseAgentTools {

  private static final int DEFAULT_TABLE_LIMIT = 100;
  private static final int MAX_TABLE_LIMIT = 500;
  private static final int MAX_COLUMNS_PER_TABLE = 100;
  private static final Map<String, Object> EXECUTE_SQL_SETTINGS =
      Map.of(
          "default_format", "JSON",
          "readonly", 2,
          "max_execution_time", 30,
          "max_result_rows", 1_000,
          "max_result_bytes", 1_000_000,
          "result_overflow_mode", "break");
  private static final Map<String, Object> READ_ONLY_SETTINGS =
      Map.of(
          "default_format", "JSON",
          "readonly", 2,
          "max_execution_time", 30,
          "max_result_rows", 2_500,
          "result_overflow_mode", "break");

  private final ClickHouseConnectionService service;
  private final String connectionId;
  private final Identity identity;
  private final ObjectMapper mapper;
  private final AgentToolExecutionPolicy executionPolicy;
  private final ClickHouseReadOnlySqlClassifier sqlClassifier;

  public ClickHouseAgentTools(
      ClickHouseConnectionService service, String connectionId, Identity identity) {
    this(service, connectionId, identity, new ObjectMapper(), AgentToolExecutionPolicy.untracked());
  }

  public ClickHouseAgentTools(
      ClickHouseConnectionService service,
      String connectionId,
      Identity identity,
      ObjectMapper mapper) {
    this(service, connectionId, identity, mapper, AgentToolExecutionPolicy.untracked());
  }

  public ClickHouseAgentTools(
      ClickHouseConnectionService service,
      String connectionId,
      Identity identity,
      ObjectMapper mapper,
      AgentToolExecutionPolicy executionPolicy) {
    this.service = service;
    this.connectionId = connectionId;
    this.identity = identity;
    this.mapper = mapper;
    this.executionPolicy = executionPolicy;
    this.sqlClassifier = new ClickHouseReadOnlySqlClassifier();
  }

  @Tool(
      name = "execute_sql",
      description =
          "Execute one read-only ClickHouse query with server-enforced row, byte, and time limits.",
      readOnly = true)
  public Mono<String> executeSql(
      @ToolParam(name = "sql", required = true, description = "ClickHouse SQL to execute")
          String sql) {
    String safeSql;
    try {
      safeSql = sqlClassifier.requireReadOnly(sql);
    } catch (IllegalArgumentException error) {
      return Mono.error(error);
    }
    return executionPolicy.guard(
        "execute_sql",
        service
            .query(connectionId, safeSql, EXECUTE_SQL_SETTINGS, identity)
            .map(this::executeSqlJson)
            .onErrorResume(this::executeSqlFailure));
  }

  @Tool(
      name = "get_tables",
      description =
          "List tables using at least one database, name, engine, or partition-key filter.",
      readOnly = true)
  public Mono<String> getTables(
      @ToolParam(
              name = "name_pattern",
              required = false,
              description = "SQL LIKE pattern for table name")
          String namePattern,
      @ToolParam(name = "database", required = false, description = "Exact database name")
          String database,
      @ToolParam(name = "engine", required = false, description = "Engine name or suffix")
          String engine,
      @ToolParam(
              name = "partition_key",
              required = false,
              description = "SQL LIKE pattern for partition key")
          String partitionKey,
      @ToolParam(
              name = "limit",
              required = false,
              description = "Maximum tables, default 100 and maximum 500")
          Integer limit) {
    if (blank(namePattern) && blank(database) && blank(engine) && blank(partitionKey)) {
      return Mono.error(new IllegalArgumentException("get_tables requires at least one filter"));
    }
    int safeLimit = bounded(limit, DEFAULT_TABLE_LIMIT, 1, MAX_TABLE_LIMIT);
    List<String> predicates =
        new ArrayList<>(
            List.of("database NOT IN ('system', 'INFORMATION_SCHEMA', 'information_schema')"));
    addEquals(predicates, "database", database);
    addLike(predicates, "name", namePattern);
    addLike(predicates, "engine", engine);
    addLike(predicates, "partition_key", partitionKey);
    String sql =
        "SELECT database, name AS table, engine, partition_key"
            + " FROM system.tables WHERE "
            + String.join(" AND ", predicates)
            + " ORDER BY database, name LIMIT "
            + safeLimit;
    return executionPolicy.guard(
        "get_tables",
        service.query(connectionId, sql, READ_ONLY_SETTINGS, identity).map(this::dataArrayJson));
  }

  @Tool(
      name = "explore_schema",
      description =
          "Explore one or more database.table schemas; optionally restrict returned columns.",
      readOnly = true)
  public Mono<String> exploreSchema(
      @ToolParam(name = "tables", required = true, description = "Tables to inspect")
          List<SchemaTableRequest> tables) {
    if (tables == null || tables.isEmpty()) {
      return Mono.error(new IllegalArgumentException("explore_schema requires at least one table"));
    }
    if (tables.size() > 20) {
      return Mono.error(new IllegalArgumentException("explore_schema accepts at most 20 tables"));
    }
    List<String> tablePredicates = new ArrayList<>();
    for (SchemaTableRequest request : tables) {
      QualifiedTable qualified = qualifiedTable(request.table());
      if (request.columns() != null && request.columns().size() > MAX_COLUMNS_PER_TABLE) {
        return Mono.error(
            new IllegalArgumentException("explore_schema accepts at most 100 columns per table"));
      }
      List<String> predicates =
          new ArrayList<>(
              List.of(
                  "(c.database = '"
                      + literal(qualified.database())
                      + "' AND c.table = '"
                      + literal(qualified.table())
                      + "'"));
      if (request.columns() != null && !request.columns().isEmpty()) {
        predicates.add(
            "c.name IN ("
                + request.columns().stream()
                    .map(column -> "'" + literal(column) + "'")
                    .collect(java.util.stream.Collectors.joining(","))
                + ")");
      }
      tablePredicates.add(String.join(" AND ", predicates) + ")");
    }
    String sql =
        "SELECT c.database AS database, c.table AS table, c.name AS name, c.type AS type,"
            + " c.comment AS comment, c.position AS position,"
            + " t.engine, t.sorting_key, t.primary_key, t.partition_key, totals.total_columns"
            + " FROM system.columns AS c INNER JOIN system.tables AS t"
            + " ON c.database = t.database AND c.table = t.name"
            + " INNER JOIN (SELECT database, table, count() AS total_columns"
            + " FROM system.columns GROUP BY database, table) AS totals"
            + " ON c.database = totals.database AND c.table = totals.table"
            + " WHERE "
            + String.join(" OR ", tablePredicates)
            + " ORDER BY c.database, c.table, c.position"
            + " LIMIT "
            + MAX_COLUMNS_PER_TABLE
            + " BY c.database, c.table";
    return executionPolicy.guard(
        "explore_schema",
        service
            .query(connectionId, sql, READ_ONLY_SETTINGS, identity)
            .map(raw -> schemaJson(raw, tables)));
  }

  @Tool(
      name = "validate_sql",
      description = "Validate ClickHouse SQL syntax without executing the statement.",
      readOnly = true)
  public Mono<String> validateSql(
      @ToolParam(name = "sql", required = true, description = "ClickHouse SQL to validate")
          String sql) {
    if (sql == null || sql.isBlank()) {
      return Mono.just(validationJson(false, "SQL must not be blank"));
    }
    return executionPolicy.guard(
        "validate_sql",
        service
            .query(connectionId, "EXPLAIN SYNTAX " + sql, READ_ONLY_SETTINGS, identity)
            .map(ignored -> validationJson(true, null))
            .onErrorResume(this::validationFailure));
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

  private String dataArrayJson(String raw) {
    try {
      return mapper.writeValueAsString(mapper.readTree(raw).path("data"));
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid ClickHouse JSON response", error);
    }
  }

  private String executeSqlJson(String raw) {
    try {
      JsonNode response = mapper.readTree(raw);
      ObjectNode result = mapper.createObjectNode();
      ArrayNode columns = result.putArray("columns");
      response
          .path("meta")
          .forEach(
              metadata -> {
                ObjectNode column = columns.addObject();
                column.put("name", metadata.path("name").asText());
                column.put("type", metadata.path("type").asText());
              });
      ArrayNode rows = result.putArray("rows");
      response.path("data").forEach(rows::add);
      result.put("rowCount", rows.size());
      if (!rows.isEmpty()) {
        result.set("sampleRow", rows.get(0));
      }
      return mapper.writeValueAsString(result);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid ClickHouse JSON response", error);
    }
  }

  private Mono<String> executeSqlFailure(Throwable error) {
    if (error instanceof ProviderOperationException provider
        && "CLICKHOUSE_QUERY_FAILED".equals(provider.code())) {
      ObjectNode result = mapper.createObjectNode();
      result.putArray("columns");
      result.put("rowCount", 0);
      result.put("error", safeMessage(error));
      return Mono.just(result.toString());
    }
    return Mono.error(error);
  }

  private String schemaJson(String raw, List<SchemaTableRequest> requests) {
    try {
      JsonNode data = mapper.readTree(raw).path("data");
      ArrayNode result = mapper.createArrayNode();
      for (SchemaTableRequest request : requests) {
        QualifiedTable qualified = qualifiedTable(request.table());
        List<JsonNode> matches = new ArrayList<>();
        data.forEach(
            row -> {
              if (qualified.database().equals(row.path("database").asText())
                  && qualified.table().equals(row.path("table").asText())) {
                matches.add(row);
              }
            });
        JsonNode metadata = matches.isEmpty() ? mapper.createObjectNode() : matches.get(0);
        int totalColumns = metadata.path("total_columns").asInt(matches.size());
        boolean truncated =
            (request.columns() == null || request.columns().isEmpty())
                && totalColumns > MAX_COLUMNS_PER_TABLE;
        ObjectNode table = result.addObject();
        table.put("database", qualified.database());
        table.put("table", qualified.table());
        ArrayNode columns = table.putArray("columns");
        matches.stream()
            .limit(MAX_COLUMNS_PER_TABLE)
            .forEach(
                row -> {
                  ObjectNode column = columns.addObject();
                  column.put("name", row.path("name").asText());
                  column.put("type", row.path("type").asText());
                  if (!row.path("comment").asText().isBlank()) {
                    column.put("comment", row.path("comment").asText());
                  }
                });
        table.put("engine", metadata.path("engine").asText());
        table.put("sortingKey", metadata.path("sorting_key").asText());
        table.put("primaryKey", metadata.path("primary_key").asText());
        table.put("partitionBy", metadata.path("partition_key").asText());
        table.put("totalColumns", totalColumns);
        table.put("truncated", truncated);
        if (truncated) {
          table.put("guidance", "Retry with a narrower columns list.");
        }
      }
      return mapper.writeValueAsString(result);
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid ClickHouse JSON response", error);
    }
  }

  private String validationJson(boolean success, String error) {
    ObjectNode result = mapper.createObjectNode().put("success", success);
    if (error != null) {
      result.put("error", error);
    }
    return result.toString();
  }

  private Mono<String> validationFailure(Throwable error) {
    if (error instanceof ProviderOperationException provider
        && "CLICKHOUSE_QUERY_FAILED".equals(provider.code())) {
      return Mono.just(validationJson(false, safeMessage(error)));
    }
    return Mono.error(error);
  }

  private static String safeMessage(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return "SQL validation failed";
    }
    return message.length() <= 2_000 ? message : message.substring(0, 2_000);
  }

  private static QualifiedTable qualifiedTable(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Table must use database.table format");
    }
    int separator = value.indexOf('.');
    if (separator <= 0
        || separator == value.length() - 1
        || value.indexOf('.', separator + 1) >= 0) {
      throw new IllegalArgumentException("Table must use database.table format: " + value);
    }
    return new QualifiedTable(value.substring(0, separator), value.substring(separator + 1));
  }

  private static void addEquals(List<String> predicates, String column, String value) {
    if (!blank(value)) {
      predicates.add(column + " = '" + literal(value) + "'");
    }
  }

  private static void addLike(List<String> predicates, String column, String value) {
    if (!blank(value)) {
      predicates.add(column + " LIKE '" + literal(value) + "'");
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static int bounded(Integer value, int fallback, int min, int max) {
    int resolved = value == null ? fallback : value;
    return Math.max(min, Math.min(max, resolved));
  }

  public record SchemaTableRequest(String table, List<String> columns) {}

  private record QualifiedTable(String database, String table) {}
}
