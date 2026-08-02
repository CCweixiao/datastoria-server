package io.github.ccweixiao.datastoria.service.approval;

import java.util.HashSet;
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

  public Mono<DdlSchemaSnapshot> inspect(
      String connectionId, String database, String table, Identity identity) {
    return connections
        .query(connectionId, QUERY, Map.of("database", database, "table", table), identity)
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
}
