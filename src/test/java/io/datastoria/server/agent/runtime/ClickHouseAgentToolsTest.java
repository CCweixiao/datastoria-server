package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.service.ClickHouseConnectionService;
import io.datastoria.server.service.RcaTemplateCatalog;

import reactor.core.publisher.Mono;

class ClickHouseAgentToolsTest {

  @Test
  void exposesAllBrowserIndependentAgentScopeTools() {
    Set<String> names =
        Arrays.stream(ClickHouseAgentTools.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(Tool.class))
            .filter(java.util.Objects::nonNull)
            .map(Tool::name)
            .collect(Collectors.toSet());

    assertThat(names)
        .containsExactlyInAnyOrder(
            "execute_sql",
            "get_tables",
            "explore_schema",
            "validate_sql",
            "collect_sql_optimization_evidence",
            "search_query_log",
            "collect_cluster_status",
            "collect_rca_evidence");
  }

  @Test
  void p6ToolSchemasMatchFrontendInputContract() {
    Toolkit toolkit = new Toolkit();
    toolkit.registerTool(new ClickHouseAgentTools(null, null, null));

    Map<String, Object> getTables = schema(toolkit, "get_tables");
    assertThat(properties(getTables))
        .containsOnlyKeys("name_pattern", "database", "engine", "partition_key", "limit");
    assertThat(getTables.get("required")).isNull();

    Map<String, Object> exploreSchema = schema(toolkit, "explore_schema");
    assertThat(properties(exploreSchema)).containsOnlyKeys("tables");
    assertThat(exploreSchema.get("required")).isEqualTo(List.of("tables"));

    Map<String, Object> validateSql = schema(toolkit, "validate_sql");
    assertThat(properties(validateSql)).containsOnlyKeys("sql");
    assertThat(validateSql.get("required")).isEqualTo(List.of("sql"));
  }

  @Test
  void rejectsUnboundedDiscoveryAndInvalidQualifiedTableBeforeNetworkCall() {
    ClickHouseAgentTools tools = new ClickHouseAgentTools(null, null, null);

    assertThatThrownBy(() -> tools.getTables(null, null, null, null, null).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one filter");
    assertThatThrownBy(
            () ->
                tools
                    .exploreSchema(
                        List.of(new ClickHouseAgentTools.SchemaTableRequest("events", List.of())))
                    .block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("database.table");
  }

  @Test
  void agentScopeInputsMatchSharedFrontendGoldenFixture() throws Exception {
    JsonNode contract =
        new ObjectMapper()
            .readTree(
                java.nio.file.Files.readString(
                    java.nio.file.Path.of("docs/fixtures/tools/p6-readonly-contract.json")));
    Toolkit toolkit = new Toolkit();
    toolkit.registerTool(new ClickHouseAgentTools(null, null, null));

    for (String name : List.of("get_tables", "explore_schema", "validate_sql")) {
      assertThat(properties(schema(toolkit, name)).keySet())
          .containsAll(iterableFieldNames(contract.path(name).path("input")));
    }
  }

  @Test
  void executeSqlReturnsFrontendShapeAndEnforcesClickHouseLimits() throws Exception {
    ClickHouseConnectionService service = mock(ClickHouseConnectionService.class);
    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<Map<String, Object>> settings =
        org.mockito.ArgumentCaptor.forClass(Map.class);
    when(service.query(anyString(), anyString(), settings.capture(), any()))
        .thenReturn(
            Mono.just(
                """
                {
                  "meta":[{"name":"value","type":"UInt64"}],
                  "data":[{"value":1},{"value":2}],
                  "rows":2
                }
                """));
    ClickHouseAgentTools tools =
        new ClickHouseAgentTools(service, "connection", new Identity("tenant", "user", Set.of()));

    JsonNode output =
        new ObjectMapper()
            .readTree(tools.executeSql("SELECT number + 1 AS value FROM numbers(2)").block());

    assertThat(output.path("columns").path(0).path("name").asText()).isEqualTo("value");
    assertThat(output.path("rows").size()).isEqualTo(2);
    assertThat(output.path("rowCount").asInt()).isEqualTo(2);
    assertThat(output.path("sampleRow").path("value").asInt()).isEqualTo(1);
    assertThat(settings.getValue())
        .containsEntry("readonly", 2)
        .containsEntry("max_result_rows", 1_000)
        .containsEntry("max_result_bytes", 1_000_000)
        .containsEntry("max_execution_time", 30);
  }

  @Test
  void searchQueryLogCompilesValidatedFiltersAndReturnsFrontendShape() throws Exception {
    ClickHouseConnectionService service = mock(ClickHouseConnectionService.class);
    org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
    when(service.query(anyString(), sql.capture(), any(), any()))
        .thenReturn(
            Mono.just(
                """
                {
                  "data":[{
                    "normalized_query_hash":"42",
                    "sql_preview":"SELECT 1",
                    "execution_count":3,
                    "metric_value":30
                  }]
                }
                """));
    ClickHouseAgentTools tools =
        new ClickHouseAgentTools(service, "connection", new Identity("tenant", "user", Set.of()));

    JsonNode output =
        new ObjectMapper()
            .readTree(
                tools
                    .searchQueryLog(
                        "patterns",
                        "duration",
                        "max",
                        10,
                        60,
                        null,
                        List.of(
                            new ClickHouseAgentTools.QueryLogPredicate(
                                "query", "contains_ci", "x' OR 1=1 --")))
                    .block());

    assertThat(output.path("success").asBoolean()).isTrue();
    assertThat(output.path("mode").asText()).isEqualTo("patterns");
    assertThat(output.path("metric").asText()).isEqualTo("duration");
    assertThat(output.path("rowCount").asInt()).isEqualTo(1);
    assertThat(output.path("defaults_applied").size()).isEqualTo(3);
    assertThat(sql.getValue())
        .contains("max(query_duration_ms) AS metric_value")
        .contains("positionCaseInsensitive(query, 'x'' OR 1=1 --')")
        .contains("type = 'QueryFinish'")
        .doesNotContain("x' OR 1=1 --')");
  }

  @Test
  void clusterStatusReturnsSnapshotAndWindowContract() throws Exception {
    ClickHouseConnectionService service = mock(ClickHouseConnectionService.class);
    when(service.query(anyString(), anyString(), any(), any()))
        .thenReturn(
            Mono.just(
                """
                {"data":[{
                  "node":"local",
                  "cluster_nodes":0,
                  "unhealthy_replicas":0,
                  "active_parts":12,
                  "active_merges":1,
                  "pending_mutations":0,
                  "disk_used_percent":25.5,
                  "current_queries":2
                }]}
                """),
            Mono.just(
                """
                {"data":[
                  {"timestamp":"2026-07-25 09:00:00","value":1},
                  {"timestamp":"2026-07-25 09:05:00","value":3}
                ]}
                """));
    ClickHouseAgentTools tools =
        new ClickHouseAgentTools(service, "connection", new Identity("tenant", "user", Set.of()));

    JsonNode output =
        new ObjectMapper()
            .readTree(
                tools
                    .collectClusterStatus(
                        "windowed",
                        List.of("parts", "disk", "errors"),
                        "summary",
                        null,
                        10,
                        new ClickHouseAgentTools.ClusterWindow("errors", 60, null, 5))
                    .block());

    assertThat(output.path("success").asBoolean()).isTrue();
    assertThat(output.path("scope").asText()).isEqualTo("single_node");
    assertThat(output.path("summary").path("total_nodes").asInt()).isEqualTo(1);
    assertThat(output.path("categories").path("parts").path("value").asInt()).isEqualTo(12);
    assertThat(output.path("window").path("series").size()).isEqualTo(2);
    assertThat(output.path("window").path("summary").path("trend").asText()).isEqualTo("up");
  }

  @Test
  void optimizationEvidenceReturnsLightAndFullExplainArtifacts() throws Exception {
    ClickHouseConnectionService service = mock(ClickHouseConnectionService.class);
    when(service.query(anyString(), anyString(), any(), any()))
        .thenReturn(
            Mono.just(
                """
                {"data":[{"explain":"ReadFromMergeTree (Indexes: PrimaryKey)"}]}
                """),
            Mono.just(
                """
                {"data":[{"explain":"ExpressionTransform → MergeTreeSelect"}]}
                """));
    ClickHouseAgentTools tools =
        new ClickHouseAgentTools(service, "connection", new Identity("tenant", "user", Set.of()));

    JsonNode output =
        new ObjectMapper()
            .readTree(
                tools
                    .collectSqlOptimizationEvidence(
                        "SELECT * FROM db.events WHERE id = 1",
                        null,
                        "latency",
                        "full",
                        60,
                        null,
                        new ClickHouseAgentTools.RequestedEvidence(
                            List.of("indexes"), List.of("pipeline")))
                    .block());

    assertThat(output.path("goal").asText()).isEqualTo("latency");
    assertThat(output.path("mode").asText()).isEqualTo("full");
    assertThat(output.path("explain_index").path("raw_text").asText()).contains("PrimaryKey");
    assertThat(output.path("explain_pipeline").path("raw_text").asText())
        .contains("MergeTreeSelect");
    assertThat(output.path("requested").path("required").path(0).asText()).isEqualTo("indexes");
  }

  @Test
  void rcaEvidenceMatchesFrontendContractAndPinsA27Template() throws Exception {
    ClickHouseConnectionService service = mock(ClickHouseConnectionService.class);
    when(service.query(anyString(), anyString(), any(), any()))
        .thenReturn(
            Mono.just(
                """
                {"data":[{
                  "database":"db",
                  "table":"events",
                  "active_parts":12,
                  "distinct_partitions":3,
                  "max_parts_per_partition":6,
                  "rows":100,
                  "bytes_on_disk":2048
                }]}
                """));
    ClickHouseAgentTools tools =
        new ClickHouseAgentTools(
            service,
            "connection",
            new Identity("tenant", "user", Set.of()),
            new ObjectMapper(),
            AgentToolExecutionPolicy.untracked(),
            new RcaTemplateCatalog.TemplateSnapshot("high_part_count", 4, "abc123"));

    JsonNode output =
        new ObjectMapper()
            .readTree(
                tools
                    .collectRcaEvidence(
                        "high_part_count",
                        "table",
                        new ClickHouseAgentTools.RcaTarget("db", "events", null, null),
                        null,
                        60,
                        null,
                        new ClickHouseAgentTools.RcaThresholds(
                            new ClickHouseAgentTools.HighPartCountThresholds(
                                null, null, 10.0, null, null, null, null, 5.0, null, null)),
                        null)
                    .block());

    assertThat(output.path("schema_version").asInt()).isEqualTo(1);
    assertThat(output.path("success").asBoolean()).isTrue();
    assertThat(output.path("scope").asText()).isEqualTo("table");
    assertThat(output.path("template").path("revision").asInt()).isEqualTo(4);
    assertThat(output.path("observations").path(0).path("metrics").path("active_parts").asInt())
        .isEqualTo(12);
    assertThat(output.path("candidates").path(0).path("indicators_matched").asInt()).isEqualTo(2);
    assertThat(output.path("possible_actions").size()).isGreaterThan(0);
    assertThat(output.path("gaps").size()).isGreaterThan(0);
  }

  private static Map<String, Object> schema(Toolkit toolkit, String name) {
    return toolkit.getToolSchemas().stream()
        .filter(schema -> name.equals(schema.getName()))
        .findFirst()
        .orElseThrow()
        .getParameters();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> properties(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
  }

  private static List<String> iterableFieldNames(JsonNode node) {
    List<String> names = new java.util.ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
