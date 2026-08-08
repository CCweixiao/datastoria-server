package io.github.ccweixiao.datastoria.service.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Mono;

@Service
public class DdlSchemaInspector {

  private static final Pattern IDENTIFIER_TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final String NODE_STATUS_QUERY =
      """
      SELECT host, port, status, exception_code, exception_text, query_duration_ms
      FROM system.distributed_ddl_queue
      WHERE positionCaseInsensitive(query, {marker:String}) > 0
        AND query_create_time >= toDateTime({since:UInt32})
      FORMAT JSONCompact
      """;
  private static final String QUERY =
      """
      SELECT
        (SELECT groupArray(name) FROM system.columns
          WHERE database = {database:String} AND table = {table:String}) AS columns,
        sorting_key, primary_key, partition_key, sampling_key
      FROM system.tables
      WHERE database = {database:String} AND name = {table:String}
      FORMAT JSONCompact
      """;

  private final ClickHouseConnectionService connections;
  private final ObjectMapper mapper;

  public DdlSchemaInspector(ClickHouseConnectionService connections, ObjectMapper mapper) {
    this.connections = connections;
    this.mapper = mapper;
  }

  /** Whether a table exists on the connection (used by the CREATE_TABLE existence branch). */
  public Mono<Boolean> objectExists(
      String connectionId, String database, String table, Identity identity) {
    return connections
        .queryReadOnly(
            connectionId,
            "SELECT count() AS c FROM system.tables"
                + " WHERE database = {database:String} AND name = {table:String} FORMAT JSONCompact",
            Map.of("param_database", database, "param_table", table),
            identity)
        .<Boolean>map(
            response -> {
              try {
                return mapper.readTree(response).path("data").path(0).path(0).asInt(0) > 0;
              } catch (Exception exception) {
                return false;
              }
            })
        .onErrorResume(exception -> Mono.just(false));
  }

  /** Whether a database exists on the connection (create-table / create-database preconditions). */
  public Mono<Boolean> databaseExists(String connectionId, String database, Identity identity) {
    return connections
        .findById(connectionId, identity)
        .flatMap(
            connection -> {
              String cluster = connection.cluster();
              String source =
                  cluster == null || cluster.isBlank()
                      ? "system.databases"
                      : "clusterAllReplicas('"
                          + escapeString(cluster.trim())
                          + "', system.databases)";
              Map<String, Object> parameters = new java.util.LinkedHashMap<>();
              parameters.put("param_database", database);
              return connections.queryReadOnly(
                  connectionId,
                  "SELECT count() AS c FROM "
                      + source
                      + " WHERE name = {database:String} FORMAT JSONCompact",
                  parameters,
                  identity);
            })
        .map(this::requiredCountResult)
        .map(count -> count > 0);
  }

  public Mono<Boolean> indexExists(
      String connectionId, String database, String table, String index, Identity identity) {
    return connections
        .queryReadOnly(
            connectionId,
            "SELECT count() AS c FROM system.data_skipping_indices"
                + " WHERE database={database:String} AND table={table:String}"
                + " AND name={index:String} FORMAT JSONCompact",
            Map.of("param_database", database, "param_table", table, "param_index", index),
            identity)
        .map(response -> countResult(response) > 0)
        .onErrorResume(exception -> Mono.just(false));
  }

  private int countResult(String response) {
    try {
      return mapper.readTree(response).path("data").path(0).path(0).asInt(0);
    } catch (Exception exception) {
      return 0;
    }
  }

  private int requiredCountResult(String response) {
    try {
      JsonNode count = mapper.readTree(response).path("data").path(0).path(0);
      if (!count.isNumber() && !count.isTextual()) {
        throw new IllegalStateException("ClickHouse count query returned no result");
      }
      return count.asInt();
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to parse ClickHouse count query result", exception);
    }
  }

  public Mono<DdlSchemaSnapshot> inspect(
      String connectionId, String database, String table, Identity identity) {
    return connections
        .queryReadOnly(
            connectionId, QUERY, Map.of("param_database", database, "param_table", table), identity)
        .map(this::parse);
  }

  DdlSchemaSnapshot parse(String response) {
    try {
      JsonNode row = mapper.readTree(response).path("data").path(0);
      if (!row.isArray()) {
        return DdlSchemaSnapshot.EMPTY;
      }
      Set<String> columns = new HashSet<>();
      row.path(0).forEach(value -> columns.add(value.asText().toLowerCase(Locale.ROOT)));
      Set<String> protectedColumns = new HashSet<>();
      for (int index = 1; index <= 4; index++) {
        Matcher matcher = IDENTIFIER_TOKEN.matcher(row.path(index).asText(""));
        while (matcher.find()) {
          String token = matcher.group().toLowerCase(Locale.ROOT);
          if (columns.contains(token)) {
            protectedColumns.add(token);
          }
        }
      }
      return new DdlSchemaSnapshot(columns, protectedColumns);
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid ClickHouse schema metadata response", exception);
    }
  }

  /**
   * Per-host execution status of an ON CLUSTER DDL, read from {@code system.distributed_ddl_queue}.
   * ClickHouse rewrites/augments the DDL before storing it (e.g. injects a UUID into CREATE
   * DATABASE), so correlation is by a stable {@code marker} (the target object name, which survives
   * rewriting) via substring match plus a time window — not by exact SQL text. The queue's {@code
   * entry} is CH's own sequential id, not the query_id we set. Returns an empty list when the
   * marker is blank or no rows are found (caller falls back to recording a single local node).
   */
  public Mono<List<NodeStatus>> nodeStatuses(
      String connectionId, String marker, Instant since, Identity identity) {
    if (marker == null || marker.isBlank()) {
      return Mono.just(List.of());
    }
    return connections
        .queryReadOnly(
            connectionId,
            NODE_STATUS_QUERY,
            Map.of("param_marker", marker, "param_since", since.getEpochSecond()),
            identity)
        .map(this::parseNodeStatuses)
        .onErrorResume(exception -> Mono.just(List.of()));
  }

  private static String escapeString(String value) {
    return value.replace("\\", "\\\\").replace("'", "\\'");
  }

  List<NodeStatus> parseNodeStatuses(String response) {
    try {
      JsonNode data = mapper.readTree(response).path("data");
      if (!data.isArray()) {
        return List.of();
      }
      List<NodeStatus> statuses = new ArrayList<>();
      for (JsonNode row : data) {
        String host = row.path(0).asText("");
        String status = row.path(2).asText("");
        long code = row.path(3).asLong(0);
        String message = row.path(4).asText("");
        long durationMs = row.path(5).asLong(0);
        boolean succeeded = "Finished".equals(status) && code == 0;
        statuses.add(
            new NodeStatus(
                host,
                row.path(1).asInt(0),
                succeeded,
                durationMs,
                code == 0 ? null : String.valueOf(code),
                message.isBlank() ? null : message));
      }
      return statuses;
    } catch (Exception exception) {
      return List.of();
    }
  }

  public record NodeStatus(
      String host,
      int port,
      boolean succeeded,
      long durationMs,
      String errorCode,
      String message) {}
}
