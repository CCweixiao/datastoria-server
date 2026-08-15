package io.github.ccweixiao.datastoria.agent.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.github.ccweixiao.datastoria.common.clickhouse.ClickHouseReadOnlySqlClassifier;
import io.github.ccweixiao.datastoria.common.error.ProviderOperationException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;
import io.github.ccweixiao.datastoria.service.RcaTemplateCatalog;

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
  private final RcaTemplateCatalog.TemplateSnapshot rcaTemplate;

  public ClickHouseAgentTools(
      ClickHouseConnectionService service, String connectionId, Identity identity) {
    this(
        service,
        connectionId,
        identity,
        new ObjectMapper(),
        AgentToolExecutionPolicy.untracked(),
        null);
  }

  public ClickHouseAgentTools(
      ClickHouseConnectionService service,
      String connectionId,
      Identity identity,
      ObjectMapper mapper) {
    this(service, connectionId, identity, mapper, AgentToolExecutionPolicy.untracked(), null);
  }

  public ClickHouseAgentTools(
      ClickHouseConnectionService service,
      String connectionId,
      Identity identity,
      ObjectMapper mapper,
      AgentToolExecutionPolicy executionPolicy) {
    this(service, connectionId, identity, mapper, executionPolicy, null);
  }

  public ClickHouseAgentTools(
      ClickHouseConnectionService service,
      String connectionId,
      Identity identity,
      ObjectMapper mapper,
      AgentToolExecutionPolicy executionPolicy,
      RcaTemplateCatalog.TemplateSnapshot rcaTemplate) {
    this.service = service;
    this.connectionId = connectionId;
    this.identity = identity;
    this.mapper = mapper;
    this.executionPolicy = executionPolicy;
    this.sqlClassifier = new ClickHouseReadOnlySqlClassifier();
    this.rcaTemplate = rcaTemplate;
  }

  @Tool(
      name = "execute_sql",
      description =
          "Execute one read-only ClickHouse query with server-enforced row, byte, and time limits.",
      readOnly = true)
  public Mono<String> executeSql(
      @ToolParam(name = "sql", required = true, description = "ClickHouse SQL to execute")
          String sql) {
    return executionPolicy.guard(
        "execute_sql",
        service
            .findById(connectionId, identity)
            .flatMap(
                connection -> {
                  String safeSql = sqlClassifier.requireReadOnly(sql, connection.cluster());
                  return service.queryReadOnly(
                      connectionId, safeSql, EXECUTE_SQL_SETTINGS, identity);
                })
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
        service
            .queryReadOnly(connectionId, sql, READ_ONLY_SETTINGS, identity)
            .map(this::dataArrayJson));
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
            .queryReadOnly(connectionId, sql, READ_ONLY_SETTINGS, identity)
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
            .queryReadOnly(connectionId, "EXPLAIN SYNTAX " + sql, READ_ONLY_SETTINGS, identity)
            .map(ignored -> validationJson(true, null))
            .onErrorResume(this::validationFailure));
  }

  @Tool(
      name = "collect_sql_optimization_evidence",
      description =
          "Collect ClickHouse optimizer evidence for one SELECT statement using EXPLAIN indexes.",
      readOnly = true)
  public Mono<String> collectSqlOptimizationEvidence(
      @ToolParam(name = "sql", required = false, description = "SQL text to analyze") String sql,
      @ToolParam(name = "query_id", required = false, description = "Query id to retrieve")
          String queryId,
      @ToolParam(name = "goal", required = false, description = "Optimization goal") String goal,
      @ToolParam(name = "mode", required = false, description = "light or full") String mode,
      @ToolParam(name = "time_window", required = false, description = "Lookback minutes")
          Integer timeWindow,
      @ToolParam(name = "time_range", required = false, description = "Absolute ISO time range")
          TimeRange timeRange,
      @ToolParam(name = "requested", required = false, description = "Requested evidence groups")
          RequestedEvidence requested) {
    if (blank(sql) && blank(queryId)) {
      return Mono.error(new IllegalArgumentException("sql or query_id is required"));
    }
    String resolvedGoal = blank(goal) ? "other" : goal;
    if (!Set.of("latency", "memory", "bytes", "dashboard", "other").contains(resolvedGoal)) {
      return Mono.error(new IllegalArgumentException("Unsupported optimization goal"));
    }
    String resolvedMode = blank(mode) ? "light" : mode;
    if (!Set.of("light", "full").contains(resolvedMode)) {
      return Mono.error(new IllegalArgumentException("mode must be light or full"));
    }
    if (timeWindow != null && timeRange != null) {
      return Mono.error(new IllegalArgumentException("Use time_window or time_range, not both"));
    }
    if (timeWindow != null && (timeWindow < 5 || timeWindow > 1_440)) {
      return Mono.error(new IllegalArgumentException("time_window must be between 5 and 1440"));
    }
    if (timeRange != null) {
      requireIsoTime(timeRange.from());
      requireIsoTime(timeRange.to());
    }
    Mono<String> resolvedSql;
    if (!blank(sql)) {
      resolvedSql = Mono.just(sql);
    } else {
      String lookupTime =
          timeRange != null
              ? " AND event_time >= parseDateTimeBestEffort('"
                  + literal(timeRange.from())
                  + "') AND event_time <= parseDateTimeBestEffort('"
                  + literal(timeRange.to())
                  + "')"
              : " AND event_time >= now() - INTERVAL "
                  + bounded(timeWindow, 60, 5, 1_440)
                  + " MINUTE";
      String lookup =
          "SELECT query FROM system.query_log WHERE query_id = '"
              + literal(queryId)
              + "' AND type = 'QueryFinish'"
              + lookupTime
              + " ORDER BY event_time DESC LIMIT 1";
      resolvedSql =
          service
              .queryReadOnly(connectionId, lookup, READ_ONLY_SETTINGS, identity)
              .map(this::firstQueryText);
    }
    Mono<String> operation =
        resolvedSql.flatMap(
            candidate -> {
              String safeSql = sqlClassifier.requireReadOnly(candidate);
              Mono<String> indexes =
                  service.queryReadOnly(
                      connectionId, "EXPLAIN indexes = 1 " + safeSql, READ_ONLY_SETTINGS, identity);
              if (!"full".equals(resolvedMode)) {
                return indexes.map(
                    raw ->
                        optimizationEvidenceJson(
                            safeSql, queryId, resolvedGoal, resolvedMode, raw, null, requested));
              }
              Mono<String> pipeline =
                  service.queryReadOnly(
                      connectionId, "EXPLAIN PIPELINE " + safeSql, READ_ONLY_SETTINGS, identity);
              return Mono.zip(indexes, pipeline)
                  .map(
                      evidence ->
                          optimizationEvidenceJson(
                              safeSql,
                              queryId,
                              resolvedGoal,
                              resolvedMode,
                              evidence.getT1(),
                              evidence.getT2(),
                              requested));
            });
    return executionPolicy.guard("collect_sql_optimization_evidence", operation);
  }

  @Tool(
      name = "search_query_log",
      description =
          "Search recent ClickHouse query_log entries by case-insensitive query text. "
              + "Returns execution metrics and errors.",
      readOnly = true)
  public Mono<String> searchQueryLog(
      @ToolParam(name = "mode", required = false, description = "patterns or executions")
          String mode,
      @ToolParam(name = "metric", required = false, description = "Optional ranking metric")
          String metric,
      @ToolParam(name = "metric_aggregation", required = false, description = "sum, avg, or max")
          String metricAggregation,
      @ToolParam(name = "limit", required = false, description = "Maximum rows, 1-100")
          Integer limit,
      @ToolParam(name = "time_window", required = false, description = "Lookback minutes, 5-10080")
          Integer timeWindow,
      @ToolParam(name = "time_range", required = false, description = "Absolute ISO time range")
          TimeRange timeRange,
      @ToolParam(name = "predicates", required = false, description = "Validated query-log filters")
          List<QueryLogPredicate> predicates) {
    QueryLogQuery query =
        buildQueryLogQuery(
            mode, metric, metricAggregation, limit, timeWindow, timeRange, predicates);
    return executionPolicy.guard(
        "search_query_log",
        service
            .queryReadOnly(connectionId, query.sql(), READ_ONLY_SETTINGS, identity)
            .map(raw -> queryLogJson(raw, query)));
  }

  @Tool(
      name = "collect_cluster_status",
      description =
          "Collect current ClickHouse cluster, replica, part, merge and mutation status from "
              + "server-side system tables.",
      readOnly = true)
  public Mono<String> collectClusterStatus(
      @ToolParam(
              name = "status_analysis_mode",
              required = false,
              description = "snapshot or windowed")
          String statusAnalysisMode,
      @ToolParam(name = "checks", required = false, description = "Health categories")
          List<String> checks,
      @ToolParam(name = "verbosity", required = false, description = "summary or detailed")
          String verbosity,
      @ToolParam(name = "thresholds", required = false, description = "Threshold overrides")
          ClusterThresholds thresholds,
      @ToolParam(name = "max_outliers", required = false, description = "Maximum outliers")
          Integer maxOutliers,
      @ToolParam(name = "window", required = false, description = "Windowed analysis options")
          ClusterWindow window) {
    String mode = blank(statusAnalysisMode) ? "snapshot" : statusAnalysisMode;
    if (!Set.of("snapshot", "windowed").contains(mode)) {
      return Mono.error(
          new IllegalArgumentException("status_analysis_mode must be snapshot or windowed"));
    }
    if (!blank(verbosity) && !Set.of("summary", "detailed").contains(verbosity)) {
      return Mono.error(new IllegalArgumentException("verbosity must be summary or detailed"));
    }
    Set<String> requestedChecks =
        checks == null || checks.isEmpty()
            ? Set.of(
                "replication",
                "disk",
                "memory",
                "cpu",
                "merges",
                "mutations",
                "parts",
                "errors",
                "connections",
                "select_queries",
                "insert_queries",
                "ddl_queries")
            : Set.copyOf(checks);
    Set<String> allowedChecks =
        Set.of(
            "replication",
            "disk",
            "memory",
            "cpu",
            "merges",
            "mutations",
            "parts",
            "errors",
            "connections",
            "select_queries",
            "insert_queries",
            "ddl_queries");
    if (!allowedChecks.containsAll(requestedChecks)) {
      return Mono.error(new IllegalArgumentException("Unsupported cluster status check"));
    }
    ClusterThresholds effective =
        thresholds == null ? ClusterThresholds.defaults() : thresholds.withDefaults();
    int outlierLimit = bounded(maxOutliers, 10, 1, 100);
    String sql =
        "SELECT hostname() AS node,"
            + " (SELECT count() FROM system.clusters) AS cluster_nodes,"
            + " (SELECT count() FROM system.replicas WHERE is_readonly OR is_session_expired)"
            + " AS unhealthy_replicas,"
            + " (SELECT count() FROM system.parts WHERE active) AS active_parts,"
            + " (SELECT count() FROM system.merges) AS active_merges,"
            + " (SELECT count() FROM system.mutations WHERE NOT is_done) AS pending_mutations,"
            + " (SELECT ifNull(max(if(total_space > 0,"
            + " (total_space - free_space) * 100.0 / total_space, 0)), 0)"
            + " FROM system.disks) AS disk_used_percent,"
            + " (SELECT count() FROM system.processes) AS current_queries";
    Mono<String> operation =
        service
            .queryReadOnly(connectionId, sql, READ_ONLY_SETTINGS, identity)
            .flatMap(
                raw -> {
                  if (!"windowed".equals(mode)) {
                    return Mono.just(
                        clusterStatusJson(
                            raw, mode, requestedChecks, effective, outlierLimit, null));
                  }
                  ClusterWindow effectiveWindow =
                      window == null ? ClusterWindow.defaults() : window.withDefaults();
                  return collectClusterWindow(effectiveWindow)
                      .map(
                          windowResult ->
                              clusterStatusJson(
                                  raw,
                                  mode,
                                  requestedChecks,
                                  effective,
                                  outlierLimit,
                                  windowResult));
                });
    return executionPolicy.guard("collect_cluster_status", operation);
  }

  @Tool(
      name = "collect_rca_evidence",
      description = "Collect structured server-side RCA evidence using the enabled A27 template.",
      readOnly = true)
  public Mono<String> collectRcaEvidence(
      @ToolParam(name = "symptom", required = true, description = "high_part_count or unknown")
          String symptom,
      @ToolParam(
              name = "scope",
              required = false,
              description = "cluster, node, table, or query_pattern")
          String scope,
      @ToolParam(name = "target", required = false, description = "Optional diagnostic target")
          RcaTarget target,
      @ToolParam(
              name = "symptom_text",
              required = false,
              description = "Required natural-language description for unknown symptoms")
          String symptomText,
      @ToolParam(name = "time_window", required = false, description = "Lookback minutes")
          Integer timeWindow,
      @ToolParam(name = "time_range", required = false, description = "Absolute ISO time range")
          TimeRange timeRange,
      @ToolParam(name = "thresholds", required = false, description = "RCA threshold overrides")
          RcaThresholds thresholds,
      @ToolParam(
              name = "status_context",
              required = false,
              description = "Optional collect_cluster_status context")
          RcaStatusContext statusContext) {
    if (!Set.of("high_part_count", "unknown").contains(symptom)) {
      return Mono.error(new IllegalArgumentException("Unsupported RCA symptom"));
    }
    String resolvedScope = blank(scope) ? inferRcaScope(target) : scope;
    if (!Set.of("cluster", "node", "table", "query_pattern").contains(resolvedScope)) {
      return Mono.error(new IllegalArgumentException("Unsupported RCA scope"));
    }
    if ("unknown".equals(symptom)) {
      if (blank(symptomText)) {
        return Mono.error(
            new IllegalArgumentException("symptom_text is required for unknown symptoms"));
      }
      return Mono.just(unknownRcaJson(resolvedScope, target, symptomText, statusContext));
    }
    if (rcaTemplate == null) {
      return Mono.error(
          new IllegalStateException("Enabled RCA template not found: high_part_count"));
    }
    if ("table".equals(resolvedScope)
        && (target == null || blank(target.database()) || blank(target.table()))) {
      return Mono.error(
          new IllegalArgumentException("table scope requires target.database and target.table"));
    }
    if (timeWindow != null && timeRange != null) {
      return Mono.error(new IllegalArgumentException("Use time_window or time_range, not both"));
    }
    int effectiveWindow = bounded(timeWindow, 60, 5, 10_080);
    if (timeRange != null) {
      requireIsoTime(timeRange.from());
      requireIsoTime(timeRange.to());
    }
    HighPartCountThresholds effectiveThresholds =
        thresholds == null || thresholds.high_part_count() == null
            ? HighPartCountThresholds.defaults()
            : thresholds.high_part_count().withDefaults();
    String database = target == null ? null : target.database();
    String table = target == null ? null : target.table();
    String predicate =
        blank(database)
            ? ""
            : " AND database = '"
                + literal(database)
                + "'"
                + (blank(table) ? "" : " AND table = '" + literal(table) + "'");
    String sql =
        "SELECT database, table, sum(parts_per_partition) AS active_parts,"
            + " uniqExact(partition) AS distinct_partitions,"
            + " max(parts_per_partition) AS max_parts_per_partition,"
            + " sum(rows) AS rows, sum(bytes_on_disk) AS bytes_on_disk"
            + " FROM (SELECT database, table, partition, count() AS parts_per_partition,"
            + " sum(rows) AS rows, sum(bytes_on_disk) AS bytes_on_disk"
            + " FROM system.parts WHERE active"
            + predicate
            + " GROUP BY database, table, partition)"
            + " GROUP BY database, table ORDER BY active_parts DESC LIMIT 100";
    Mono<String> operation =
        service
            .queryReadOnly(connectionId, sql, READ_ONLY_SETTINGS, identity)
            .map(
                raw ->
                    highPartCountRcaJson(
                        raw,
                        resolvedScope,
                        target,
                        effectiveWindow,
                        timeRange,
                        effectiveThresholds,
                        statusContext));
    return executionPolicy.guard("collect_rca_evidence", operation);
  }

  private static String inferRcaScope(RcaTarget target) {
    if (target == null) {
      return "cluster";
    }
    if (!blank(target.database()) && !blank(target.table())) {
      return "table";
    }
    if (!blank(target.node())) {
      return "node";
    }
    if (!blank(target.query_hash())) {
      return "query_pattern";
    }
    return "cluster";
  }

  private String unknownRcaJson(
      String scope, RcaTarget target, String symptomText, RcaStatusContext statusContext) {
    ObjectNode result = rcaEnvelope("unknown", scope, target);
    ArrayNode observations = result.putArray("observations");
    ObjectNode observation = observations.addObject();
    observation.put("source", statusContext == null ? "user_report" : "collect_cluster_status");
    observation.put("description", symptomText);
    ObjectNode metrics = observation.putObject("metrics");
    metrics.put("status_context_available", statusContext == null ? 0 : 1);
    result.putArray("candidates");
    result.putArray("possible_actions");
    ArrayNode gaps = result.putArray("gaps");
    ObjectNode gap = gaps.addObject();
    gap.put("description", "No enabled RCA template matches the reported symptom.");
    gap.put("reason", "Use targeted read-only probes before assigning a root cause.");
    result.put("generated_at", java.time.Instant.now().toString());
    return result.toString();
  }

  private String highPartCountRcaJson(
      String raw,
      String scope,
      RcaTarget target,
      int timeWindow,
      TimeRange timeRange,
      HighPartCountThresholds thresholds,
      RcaStatusContext statusContext) {
    try {
      ArrayNode rows = (ArrayNode) mapper.readTree(raw).path("data");
      long totalParts = 0;
      long distinctPartitions = 0;
      long maxPartsPerPartition = 0;
      for (JsonNode row : rows) {
        totalParts += row.path("active_parts").asLong();
        distinctPartitions += row.path("distinct_partitions").asLong();
        maxPartsPerPartition =
            Math.max(maxPartsPerPartition, row.path("max_parts_per_partition").asLong());
      }

      ObjectNode result = rcaEnvelope("high_part_count", scope, target);
      ObjectNode template = result.putObject("template");
      template.put("key", rcaTemplate.key());
      template.put("revision", rcaTemplate.revision());
      template.put("checksum", rcaTemplate.checksum());

      ArrayNode observations = result.putArray("observations");
      ObjectNode inventory = observations.addObject();
      inventory.put("source", "system.parts");
      inventory.put("description", "Active MergeTree part inventory in the requested scope.");
      ObjectNode inventoryMetrics = inventory.putObject("metrics");
      inventoryMetrics.put("active_parts", totalParts);
      inventoryMetrics.put("distinct_partitions", distinctPartitions);
      inventoryMetrics.put("max_parts_per_partition", maxPartsPerPartition);
      inventoryMetrics.put("tables", rows.size());
      ObjectNode summary = inventory.putObject("scope_summary");
      summary.put("level", Set.of("cluster", "node", "table").contains(scope) ? scope : "cluster");
      summary.put("aggregation_semantics", "inventory");
      summary.put(
          "cluster_aggregation", "sum across returned tables; maximum for partition hotspot");

      if (statusContext != null) {
        ObjectNode status = observations.addObject();
        status.put("source", "collect_cluster_status");
        status.put("description", "Caller-supplied status snapshot used as supporting context.");
        ObjectNode statusMetrics = status.putObject("metrics");
        statusMetrics.put("generated_at", statusContext.generated_at());
        statusMetrics.put("analysis_mode", statusContext.status_analysis_mode());
      }

      boolean totalExceeded = totalParts > thresholds.total_active_parts_gt();
      boolean partitionExceeded = maxPartsPerPartition > thresholds.max_parts_per_partition_gt();
      boolean partitionsExceeded = distinctPartitions > thresholds.distinct_partitions_gt();
      int matched =
          (totalExceeded ? 1 : 0) + (partitionExceeded ? 1 : 0) + (partitionsExceeded ? 1 : 0);
      ArrayNode candidates = result.putArray("candidates");
      ObjectNode candidate = candidates.addObject();
      candidate.put("cause", "part_inventory_pressure");
      candidate.put("support_score", matched / 3.0);
      candidate.put("indicators_matched", matched);
      candidate.put("indicators_checked", 3);
      ArrayNode evidenceFor = candidate.putArray("evidence_for");
      if (totalExceeded) {
        evidenceFor.add(
            "active_parts " + totalParts + " exceeds " + thresholds.total_active_parts_gt());
      }
      if (partitionExceeded) {
        evidenceFor.add(
            "max_parts_per_partition "
                + maxPartsPerPartition
                + " exceeds "
                + thresholds.max_parts_per_partition_gt());
      }
      if (partitionsExceeded) {
        evidenceFor.add(
            "distinct_partitions "
                + distinctPartitions
                + " exceeds "
                + thresholds.distinct_partitions_gt());
      }
      ArrayNode evidenceAgainst = candidate.putArray("evidence_against");
      if (matched == 0) {
        evidenceAgainst.add("No configured high-part-count inventory threshold was exceeded.");
      }
      candidate
          .putArray("next_checks")
          .add("Inspect insert frequency and average rows per insert in system.query_log.")
          .add("Inspect active merges and the effective parts_to_throw_insert setting.");

      ArrayNode actions = result.putArray("possible_actions");
      addRcaAction(
          actions,
          "Increase insert batch size and reduce insert frequency",
          "low",
          "insert_too_frequent");
      addRcaAction(
          actions, "Investigate merge backlog and node merge pressure", "medium", "merge_backlog");
      addRcaAction(
          actions,
          "Review partition key granularity and lifecycle alignment",
          "high",
          "partition_granularity_pressure");

      ArrayNode gaps = result.putArray("gaps");
      ObjectNode gap = gaps.addObject();
      gap.put(
          "description", "Insert and merge time-series evidence was not collected in this probe.");
      gap.put(
          "reason",
          timeRange == null
              ? "Use search_query_log over the last " + timeWindow + " minutes."
              : "Use search_query_log over the requested absolute time range.");
      result.put("generated_at", java.time.Instant.now().toString());
      return result.toString();
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid ClickHouse JSON response", error);
    }
  }

  private ObjectNode rcaEnvelope(String symptom, String scope, RcaTarget target) {
    ObjectNode result = mapper.createObjectNode();
    result.put("schema_version", 1);
    result.put("success", true);
    result.put("symptom", symptom);
    result.put("scope", scope);
    if (target != null) {
      ObjectNode targetJson = result.putObject("target");
      putNonBlank(targetJson, "database", target.database());
      putNonBlank(targetJson, "table", target.table());
      putNonBlank(targetJson, "node", target.node());
      putNonBlank(targetJson, "query_hash", target.query_hash());
    }
    return result;
  }

  private static void putNonBlank(ObjectNode object, String name, String value) {
    if (!blank(value)) {
      object.put(name, value);
    }
  }

  private static void addRcaAction(ArrayNode actions, String title, String risk, String tiedTo) {
    ObjectNode action = actions.addObject();
    action.put("title", title);
    action.put("risk", risk);
    action.put("tied_to", tiedTo);
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

  private String firstQueryText(String raw) {
    try {
      String query = mapper.readTree(raw).path("data").path(0).path("query").asText();
      if (query.isBlank()) {
        throw new IllegalArgumentException("No completed query found for query_id");
      }
      return query;
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid ClickHouse JSON response", error);
    }
  }

  private String optimizationEvidenceJson(
      String sql,
      String queryId,
      String goal,
      String mode,
      String indexesRaw,
      String pipelineRaw,
      RequestedEvidence requested) {
    ObjectNode result = mapper.createObjectNode();
    result.put("goal", goal);
    result.put("mode", mode);
    result.put("sql", sql);
    if (!blank(queryId)) {
      result.put("query_id", queryId);
    }
    ObjectNode explainIndexes = result.putObject("explain_index");
    explainIndexes.put("raw_text", explainText(indexesRaw));
    explainIndexes.putArray("indexes");
    explainIndexes
        .putArray("summary")
        .add("ClickHouse EXPLAIN indexes evidence collected under readonly limits.");
    if (pipelineRaw != null) {
      ObjectNode pipeline = result.putObject("explain_pipeline");
      pipeline.put("raw_text", explainText(pipelineRaw));
      pipeline.putArray("operators");
      pipeline.putArray("summary").add("ClickHouse EXPLAIN PIPELINE evidence collected.");
    }
    ArrayNode constraints = result.putArray("constraints");
    constraints.add("read_only");
    constraints.add("max_execution_time=30");
    if (requested != null) {
      result.set("requested", mapper.valueToTree(requested));
    }
    return result.toString();
  }

  private String explainText(String raw) {
    try {
      StringBuilder text = new StringBuilder();
      mapper
          .readTree(raw)
          .path("data")
          .forEach(
              row ->
                  row.fields()
                      .forEachRemaining(
                          field -> {
                            if (!field.getValue().isContainerNode()) {
                              if (!text.isEmpty()) {
                                text.append('\n');
                              }
                              text.append(field.getValue().asText());
                            }
                          }));
      return text.toString();
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

  private QueryLogQuery buildQueryLogQuery(
      String requestedMode,
      String requestedMetric,
      String requestedAggregation,
      Integer requestedLimit,
      Integer requestedWindow,
      TimeRange timeRange,
      List<QueryLogPredicate> requestedPredicates) {
    String mode = blank(requestedMode) ? "patterns" : requestedMode;
    if (!Set.of("patterns", "executions").contains(mode)) {
      throw new IllegalArgumentException("search_query_log mode must be patterns or executions");
    }
    String aggregation = blank(requestedAggregation) ? "sum" : requestedAggregation;
    if (!Set.of("sum", "avg", "max").contains(aggregation)) {
      throw new IllegalArgumentException("metric_aggregation must be sum, avg, or max");
    }
    Map<String, String> metrics =
        Map.of(
            "cpu", "ProfileEvents['OSCPUVirtualTimeMicroseconds']",
            "memory", "memory_usage",
            "disk", "ProfileEvents['OSReadBytes']",
            "duration", "query_duration_ms",
            "read_rows", "read_rows",
            "read_bytes", "read_bytes");
    String metricExpression = null;
    if (!blank(requestedMetric)) {
      metricExpression = metrics.get(requestedMetric);
      if (metricExpression == null) {
        throw new IllegalArgumentException("Unsupported query-log metric: " + requestedMetric);
      }
    }
    int limit = bounded(requestedLimit, 10, 1, 100);
    List<String> conditions = new ArrayList<>();
    Integer timeWindow = null;
    if (timeRange != null && !blank(timeRange.from()) && !blank(timeRange.to())) {
      requireIsoTime(timeRange.from());
      requireIsoTime(timeRange.to());
      conditions.add(
          "event_time >= parseDateTimeBestEffort('"
              + literal(timeRange.from())
              + "') AND event_time <= parseDateTimeBestEffort('"
              + literal(timeRange.to())
              + "')");
    } else {
      timeWindow = bounded(requestedWindow, 60, 5, 10_080);
      conditions.add("event_time >= now() - INTERVAL " + timeWindow + " MINUTE");
    }
    List<QueryLogPredicate> predicates =
        requestedPredicates == null ? List.of() : List.copyOf(requestedPredicates);
    if (predicates.size() > 20) {
      throw new IllegalArgumentException("search_query_log accepts at most 20 predicates");
    }
    Set<String> touched = new LinkedHashSet<>();
    List<String> filtersApplied = new ArrayList<>();
    for (QueryLogPredicate predicate : predicates) {
      touched.add(predicate.field());
      conditions.add(compileQueryLogPredicate(predicate));
      filtersApplied.add(
          predicate.field()
              + " "
              + predicate.op()
              + (predicate.value() == null ? "" : " " + predicate.value()));
    }
    List<String> defaults = new ArrayList<>();
    if (!touched.contains("type")) {
      conditions.add("type = 'QueryFinish'");
      defaults.add("type = QueryFinish");
    }
    if (!touched.contains("is_initial_query")) {
      conditions.add("is_initial_query = 1");
      defaults.add("is_initial_query = 1");
    }
    if (!touched.contains("query_kind")) {
      conditions.add("query_kind = 'Select'");
      defaults.add("query_kind = Select");
    }
    String sql;
    if ("patterns".equals(mode)) {
      String metricProjection =
          metricExpression == null
              ? ""
              : ", " + aggregation + "(" + metricExpression + ") AS metric_value";
      String order =
          metricExpression == null
              ? "execution_count DESC, last_execution_time DESC"
              : "metric_value DESC, last_execution_time DESC";
      sql =
          "SELECT normalized_query_hash, any(query_id) AS sample_query_id,"
              + " any(user) AS sample_user, substring(any(query), 1, 300) AS sql_preview,"
              + " max(event_time) AS last_execution_time, count() AS execution_count,"
              + " avg(query_duration_ms) AS avg_duration_ms,"
              + " max(query_duration_ms) AS max_duration_ms,"
              + " max(memory_usage) AS max_memory_usage, sum(read_rows) AS sum_read_rows,"
              + " sum(read_bytes) AS sum_read_bytes"
              + metricProjection
              + " FROM system.query_log WHERE "
              + String.join(" AND ", conditions)
              + " GROUP BY normalized_query_hash ORDER BY "
              + order
              + " LIMIT "
              + limit;
    } else {
      String metricProjection =
          metricExpression == null ? "" : ", " + metricExpression + " AS metric_value";
      String order =
          metricExpression == null ? "event_time DESC" : "metric_value DESC, event_time DESC";
      sql =
          "SELECT query_id, user, event_time, query_kind, query_duration_ms, memory_usage,"
              + " read_rows, read_bytes, result_rows, exception, normalized_query_hash,"
              + " substring(query, 1, 300) AS sql_preview"
              + metricProjection
              + " FROM system.query_log WHERE "
              + String.join(" AND ", conditions)
              + " ORDER BY "
              + order
              + " LIMIT "
              + limit;
    }
    return new QueryLogQuery(
        sql,
        mode,
        requestedMetric,
        requestedMetric == null ? null : aggregation,
        timeWindow,
        timeRange,
        defaults,
        filtersApplied);
  }

  private String compileQueryLogPredicate(QueryLogPredicate predicate) {
    if (predicate == null || blank(predicate.field()) || blank(predicate.op())) {
      throw new IllegalArgumentException("Query-log predicate field and op are required");
    }
    Map<String, String> scalarFields =
        Map.of(
            "user", "user",
            "query_kind", "query_kind",
            "query", "query",
            "query_id", "query_id",
            "normalized_query_hash", "normalized_query_hash",
            "type", "type",
            "exception", "ifNull(exception, '')");
    Map<String, String> numericFields =
        Map.of(
            "is_initial_query", "is_initial_query",
            "query_duration_ms", "query_duration_ms",
            "read_rows", "read_rows",
            "read_bytes", "read_bytes",
            "memory_usage", "memory_usage",
            "result_rows", "result_rows");
    if ("database".equals(predicate.field()) || "table".equals(predicate.field())) {
      return compileArrayPredicate(
          "database".equals(predicate.field()) ? "databases" : "tables", predicate);
    }
    if ("has_error".equals(predicate.field())) {
      if (!Set.of("eq", "neq").contains(predicate.op())
          || !(predicate.value() instanceof Boolean value)) {
        throw new IllegalArgumentException("has_error requires eq/neq and a boolean value");
      }
      return "(ifNull(exception, '') != '') "
          + ("eq".equals(predicate.op()) ? "=" : "!=")
          + " "
          + (value ? "1" : "0");
    }
    String scalar = scalarFields.get(predicate.field());
    if (scalar != null) {
      return compileScalarPredicate(scalar, predicate);
    }
    String numeric = numericFields.get(predicate.field());
    if (numeric != null) {
      return compileNumericPredicate(numeric, predicate);
    }
    throw new IllegalArgumentException(
        "Unsupported query-log predicate field: " + predicate.field());
  }

  private String compileScalarPredicate(String expression, QueryLogPredicate predicate) {
    return switch (predicate.op()) {
      case "eq" -> expression + " = " + queryLogValue(requireScalar(predicate));
      case "neq" -> expression + " != " + queryLogValue(requireScalar(predicate));
      case "in" -> expression + " IN (" + queryLogValues(predicate) + ")";
      case "not_in" -> expression + " NOT IN (" + queryLogValues(predicate) + ")";
      case "contains_ci" -> "positionCaseInsensitive("
          + expression
          + ", "
          + queryLogValue(requireScalar(predicate))
          + ") > 0";
      case "not_contains_ci" -> "positionCaseInsensitive("
          + expression
          + ", "
          + queryLogValue(requireScalar(predicate))
          + ") = 0";
      case "is_null" -> expression + " IS NULL";
      case "not_null" -> expression + " IS NOT NULL";
      default -> throw new IllegalArgumentException("Unsupported scalar predicate op");
    };
  }

  private String compileNumericPredicate(String expression, QueryLogPredicate predicate) {
    String operator =
        switch (predicate.op()) {
          case "eq" -> "=";
          case "neq" -> "!=";
          case "gt" -> ">";
          case "gte" -> ">=";
          case "lt" -> "<";
          case "lte" -> "<=";
          default -> null;
        };
    if (operator != null) {
      Object value = requireScalar(predicate);
      if (!(value instanceof Number) && !(value instanceof Boolean)) {
        throw new IllegalArgumentException("Numeric predicate requires a number");
      }
      return expression + " " + operator + " " + queryLogValue(value);
    }
    if ("in".equals(predicate.op()) || "not_in".equals(predicate.op())) {
      return expression
          + ("in".equals(predicate.op()) ? " IN (" : " NOT IN (")
          + queryLogValues(predicate)
          + ")";
    }
    throw new IllegalArgumentException("Unsupported numeric predicate op");
  }

  private String compileArrayPredicate(String expression, QueryLogPredicate predicate) {
    return switch (predicate.op()) {
      case "eq", "has" -> "has("
          + expression
          + ", "
          + queryLogValue(requireScalar(predicate))
          + ")";
      case "neq", "not_has" -> "NOT has("
          + expression
          + ", "
          + queryLogValue(requireScalar(predicate))
          + ")";
      case "in" -> "hasAny(" + expression + ", [" + queryLogValues(predicate) + "])";
      case "not_in" -> "NOT hasAny(" + expression + ", [" + queryLogValues(predicate) + "])";
      default -> throw new IllegalArgumentException("Unsupported array predicate op");
    };
  }

  private static Object requireScalar(QueryLogPredicate predicate) {
    if (predicate.value() == null || predicate.value() instanceof List<?>) {
      throw new IllegalArgumentException("Predicate requires a scalar value");
    }
    return predicate.value();
  }

  private String queryLogValues(QueryLogPredicate predicate) {
    if (!(predicate.value() instanceof List<?> values) || values.isEmpty()) {
      throw new IllegalArgumentException("Predicate requires a non-empty array value");
    }
    return values.stream()
        .map(this::queryLogValue)
        .collect(java.util.stream.Collectors.joining(","));
  }

  private String queryLogValue(Object value) {
    if (value instanceof Number number) {
      if (!Double.isFinite(number.doubleValue())) {
        throw new IllegalArgumentException("Numeric predicate value must be finite");
      }
      return number.toString();
    }
    if (value instanceof Boolean bool) {
      return bool ? "1" : "0";
    }
    if (value instanceof String text) {
      return "'" + literal(text) + "'";
    }
    throw new IllegalArgumentException("Unsupported query-log predicate value");
  }

  private String queryLogJson(String raw, QueryLogQuery query) {
    try {
      ArrayNode rows = mapper.createArrayNode();
      mapper.readTree(raw).path("data").forEach(rows::add);
      ObjectNode result = mapper.createObjectNode();
      result.put("success", true);
      result.put("mode", query.mode());
      if (query.metric() != null) {
        result.put("metric", query.metric());
        result.put("metric_aggregation", query.metricAggregation());
      }
      if (query.timeWindow() != null) {
        result.put("time_window", query.timeWindow());
      } else if (query.timeRange() != null) {
        result.set("time_range", mapper.valueToTree(query.timeRange()));
      }
      result.set("defaults_applied", mapper.valueToTree(query.defaultsApplied()));
      result.set("filters_applied", mapper.valueToTree(query.filtersApplied()));
      result.put("rowCount", rows.size());
      result.set("rows", rows);
      if (rows.isEmpty()) {
        result.put("message", "No query_log rows matched the requested filters.");
      }
      return result.toString();
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid ClickHouse JSON response", error);
    }
  }

  private Mono<JsonNode> collectClusterWindow(ClusterWindow window) {
    if (!Set.of("errors", "query_latency", "query_performance").contains(window.metric_type())) {
      ObjectNode unsupported = mapper.createObjectNode();
      unsupported.put("success", false);
      unsupported.put("metric_type", window.metric_type());
      unsupported.put("granularity_minutes", window.granularity_minutes());
      unsupported.putArray("series");
      unsupported.put(
          "message",
          "Historical series is unavailable for this metric on the local ClickHouse profile.");
      return Mono.just(unsupported);
    }
    String valueExpression =
        switch (window.metric_type()) {
          case "errors" -> "countIf(ifNull(exception, '') != '')";
          case "query_latency" -> "quantile(0.95)(query_duration_ms)";
          default -> "avg(query_duration_ms)";
        };
    String timeFilter;
    if (window.time_range() != null
        && !blank(window.time_range().from())
        && !blank(window.time_range().to())) {
      requireIsoTime(window.time_range().from());
      requireIsoTime(window.time_range().to());
      timeFilter =
          "event_time >= parseDateTimeBestEffort('"
              + literal(window.time_range().from())
              + "') AND event_time <= parseDateTimeBestEffort('"
              + literal(window.time_range().to())
              + "')";
    } else {
      timeFilter = "event_time >= now() - INTERVAL " + window.time_window() + " MINUTE";
    }
    String sql =
        "SELECT toStartOfInterval(event_time, INTERVAL "
            + window.granularity_minutes()
            + " MINUTE) AS timestamp, "
            + valueExpression
            + " AS value FROM system.query_log WHERE "
            + timeFilter
            + " AND type IN ('QueryFinish', 'ExceptionWhileProcessing')"
            + " GROUP BY timestamp ORDER BY timestamp";
    return service
        .queryReadOnly(connectionId, sql, READ_ONLY_SETTINGS, identity)
        .map(
            raw -> {
              try {
                ArrayNode series = (ArrayNode) mapper.readTree(raw).path("data");
                ObjectNode result = mapper.createObjectNode();
                result.put("success", true);
                result.put("metric_type", window.metric_type());
                if (window.time_range() != null) {
                  result.set("time_range", mapper.valueToTree(window.time_range()));
                } else {
                  result.put("time_window", window.time_window());
                }
                result.put("granularity_minutes", window.granularity_minutes());
                result.set("series", series);
                result.set("summary", seriesSummary(series));
                return result;
              } catch (JsonProcessingException error) {
                throw new IllegalStateException("Invalid ClickHouse JSON response", error);
              }
            });
  }

  private ObjectNode seriesSummary(ArrayNode series) {
    ObjectNode summary = mapper.createObjectNode();
    if (series.isEmpty()) {
      summary.putNull("min");
      summary.putNull("max");
      summary.putNull("avg");
      summary.put("trend", "unknown");
      return summary;
    }
    double min = Double.POSITIVE_INFINITY;
    double max = Double.NEGATIVE_INFINITY;
    double sum = 0;
    for (JsonNode point : series) {
      double value = point.path("value").asDouble();
      min = Math.min(min, value);
      max = Math.max(max, value);
      sum += value;
    }
    summary.put("min", min);
    summary.put("max", max);
    summary.put("avg", sum / series.size());
    double first = series.get(0).path("value").asDouble();
    double last = series.get(series.size() - 1).path("value").asDouble();
    summary.put("trend", last > first ? "up" : last < first ? "down" : "flat");
    return summary;
  }

  private String clusterStatusJson(
      String raw,
      String mode,
      Set<String> checks,
      ClusterThresholds thresholds,
      int maxOutliers,
      JsonNode window) {
    try {
      JsonNode row = mapper.readTree(raw).path("data").path(0);
      int unhealthyReplicas = row.path("unhealthy_replicas").asInt();
      long activeParts = row.path("active_parts").asLong();
      long pendingMutations = row.path("pending_mutations").asLong();
      double diskUsed = row.path("disk_used_percent").asDouble();
      boolean issue =
          unhealthyReplicas > 0
              || activeParts >= thresholds.parts_warning()
              || pendingMutations > 0
              || diskUsed >= thresholds.disk_warning();
      ObjectNode result = mapper.createObjectNode();
      result.put("success", true);
      result.put("status_analysis_mode", mode);
      int clusterNodes = row.path("cluster_nodes").asInt();
      result.put("scope", clusterNodes > 1 ? "cluster" : "single_node");
      result.put("node_count", Math.max(1, clusterNodes));
      ObjectNode summary = result.putObject("summary");
      summary.put("total_nodes", Math.max(1, clusterNodes));
      summary.put("healthy_nodes", issue ? 0 : Math.max(1, clusterNodes));
      summary.put("nodes_with_issues", issue ? 1 : 0);
      ObjectNode categories = result.putObject("categories");
      if (checks.contains("replication")) {
        category(categories, "replication", unhealthyReplicas, unhealthyReplicas > 0);
      }
      if (checks.contains("disk")) {
        category(categories, "disk", diskUsed, diskUsed >= thresholds.disk_warning());
      }
      if (checks.contains("parts")) {
        category(categories, "parts", activeParts, activeParts >= thresholds.parts_warning());
      }
      if (checks.contains("merges")) {
        category(categories, "merges", row.path("active_merges").asLong(), false);
      }
      if (checks.contains("mutations")) {
        category(categories, "mutations", pendingMutations, pendingMutations > 0);
      }
      if (checks.contains("connections")
          || checks.contains("select_queries")
          || checks.contains("insert_queries")
          || checks.contains("ddl_queries")) {
        category(categories, "connections", row.path("current_queries").asLong(), false);
      }
      for (String check : checks) {
        if (!categories.has(check)) {
          ObjectNode unavailable = categories.putObject(check);
          unavailable.put("status", "UNKNOWN");
          unavailable.put("message", "Metric is not enabled in the local ClickHouse profile.");
          unavailable.put("max_outliers", maxOutliers);
        }
      }
      if (window != null) {
        result.set("window", window);
      }
      result.put("generated_at", java.time.Instant.now().toString());
      return result.toString();
    } catch (JsonProcessingException error) {
      throw new IllegalStateException("Invalid ClickHouse JSON response", error);
    }
  }

  private static void category(ObjectNode categories, String name, double value, boolean warning) {
    ObjectNode category = categories.putObject(name);
    category.put("status", warning ? "WARNING" : "OK");
    category.put("value", value);
  }

  private static void requireIsoTime(String value) {
    try {
      java.time.temporal.TemporalAccessor ignored =
          java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(value);
    } catch (java.time.format.DateTimeParseException error) {
      try {
        java.time.LocalDate.parse(value);
      } catch (java.time.format.DateTimeParseException alsoInvalid) {
        throw new IllegalArgumentException("Invalid ISO time: " + value);
      }
    }
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

  public record TimeRange(String from, String to) {}

  public record QueryLogPredicate(String field, String op, Object value) {}

  public record RequestedEvidence(List<String> required, List<String> optional) {}

  public record RcaTarget(String database, String table, String node, String query_hash) {}

  public record RcaThresholds(HighPartCountThresholds high_part_count) {}

  public record HighPartCountThresholds(
      Double inserts_per_minute_gt,
      Double avg_rows_per_insert_lt,
      Double total_active_parts_gt,
      Double active_merges_gt,
      Double max_merge_elapsed_seconds_gt,
      Double distinct_partitions_gt,
      Double partition_to_parts_ratio_gt,
      Double max_parts_per_partition_gt,
      Double related_symptom_distinct_partitions_gte,
      Double related_symptom_signal_strength_gte) {

    static HighPartCountThresholds defaults() {
      return new HighPartCountThresholds(
          30.0, 10_000.0, 500.0, 5.0, 300.0, 100.0, 0.5, 100.0, 100.0, 0.7);
    }

    HighPartCountThresholds withDefaults() {
      HighPartCountThresholds defaults = defaults();
      return new HighPartCountThresholds(
          inserts_per_minute_gt == null ? defaults.inserts_per_minute_gt : inserts_per_minute_gt,
          avg_rows_per_insert_lt == null ? defaults.avg_rows_per_insert_lt : avg_rows_per_insert_lt,
          total_active_parts_gt == null ? defaults.total_active_parts_gt : total_active_parts_gt,
          active_merges_gt == null ? defaults.active_merges_gt : active_merges_gt,
          max_merge_elapsed_seconds_gt == null
              ? defaults.max_merge_elapsed_seconds_gt
              : max_merge_elapsed_seconds_gt,
          distinct_partitions_gt == null ? defaults.distinct_partitions_gt : distinct_partitions_gt,
          partition_to_parts_ratio_gt == null
              ? defaults.partition_to_parts_ratio_gt
              : partition_to_parts_ratio_gt,
          max_parts_per_partition_gt == null
              ? defaults.max_parts_per_partition_gt
              : max_parts_per_partition_gt,
          related_symptom_distinct_partitions_gte == null
              ? defaults.related_symptom_distinct_partitions_gte
              : related_symptom_distinct_partitions_gte,
          related_symptom_signal_strength_gte == null
              ? defaults.related_symptom_signal_strength_gte
              : related_symptom_signal_strength_gte);
    }
  }

  public record RcaStatusContext(
      String generated_at,
      String status_analysis_mode,
      String scope,
      RcaStatusWindow window,
      Map<String, Object> categories) {}

  public record RcaStatusWindow(Integer time_window, TimeRange time_range) {}

  public record ClusterThresholds(
      Double disk_warning,
      Double disk_critical,
      Double cpu_cores_used_warning,
      Double cpu_cores_used_critical,
      Double replication_lag_warning_seconds,
      Double replication_lag_critical_seconds,
      Double parts_warning,
      Double parts_critical,
      Double query_p95_warning_ms,
      Double query_p95_critical_ms) {

    static ClusterThresholds defaults() {
      return new ClusterThresholds(
          80.0, 90.0, 4.0, 8.0, 60.0, 300.0, 500.0, 1000.0, 1000.0, 3000.0);
    }

    ClusterThresholds withDefaults() {
      ClusterThresholds defaults = defaults();
      return new ClusterThresholds(
          disk_warning == null ? defaults.disk_warning : disk_warning,
          disk_critical == null ? defaults.disk_critical : disk_critical,
          cpu_cores_used_warning == null ? defaults.cpu_cores_used_warning : cpu_cores_used_warning,
          cpu_cores_used_critical == null
              ? defaults.cpu_cores_used_critical
              : cpu_cores_used_critical,
          replication_lag_warning_seconds == null
              ? defaults.replication_lag_warning_seconds
              : replication_lag_warning_seconds,
          replication_lag_critical_seconds == null
              ? defaults.replication_lag_critical_seconds
              : replication_lag_critical_seconds,
          parts_warning == null ? defaults.parts_warning : parts_warning,
          parts_critical == null ? defaults.parts_critical : parts_critical,
          query_p95_warning_ms == null ? defaults.query_p95_warning_ms : query_p95_warning_ms,
          query_p95_critical_ms == null ? defaults.query_p95_critical_ms : query_p95_critical_ms);
    }
  }

  public record ClusterWindow(
      String metric_type, Integer time_window, TimeRange time_range, Integer granularity_minutes) {

    static ClusterWindow defaults() {
      return new ClusterWindow("errors", 60, null, 5);
    }

    ClusterWindow withDefaults() {
      String metric = blank(metric_type) ? "errors" : metric_type;
      Set<String> supported =
          Set.of(
              "replication",
              "disk",
              "memory",
              "cpu",
              "merges",
              "mutations",
              "parts",
              "errors",
              "connections",
              "query_latency",
              "query_performance");
      if (!supported.contains(metric)) {
        throw new IllegalArgumentException("Unsupported cluster window metric");
      }
      return new ClusterWindow(
          metric,
          time_range == null ? bounded(time_window, 60, 5, 10_080) : null,
          time_range,
          bounded(granularity_minutes, 5, 1, 1_440));
    }
  }

  private record QualifiedTable(String database, String table) {}

  private record QueryLogQuery(
      String sql,
      String mode,
      String metric,
      String metricAggregation,
      Integer timeWindow,
      TimeRange timeRange,
      List<String> defaultsApplied,
      List<String> filtersApplied) {}
}
