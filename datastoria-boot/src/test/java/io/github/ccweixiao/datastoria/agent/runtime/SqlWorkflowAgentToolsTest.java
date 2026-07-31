package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SqlWorkflowAgentToolsTest {

  @Test
  void generateAndOptimizeUseNestedModelThenRealValidationBoundary() throws Exception {
    ClickHouseConnectionService service = mock(ClickHouseConnectionService.class);
    when(service.query(anyString(), anyString(), any(), any()))
        .thenReturn(Mono.just("{\"data\":[{\"explain\":\"ok\"}]}"));
    ClickHouseAgentTools clickHouse =
        new ClickHouseAgentTools(service, "connection", new Identity("tenant", "user", Set.of()));
    ScriptedJsonModel model =
        new ScriptedJsonModel(
            """
            {"sql":"SELECT service, count() AS total FROM db.events GROUP BY service LIMIT 10",
             "notes":"aggregate services","assumptions":[],"needs_clarification":false,"questions":[]}
            """,
            """
            {"optimized_sql":"SELECT service, count() AS total FROM db.events GROUP BY service LIMIT 10",
             "changes":["kept evidence-supported shape"],"assumptions":[]}
            """);
    SqlWorkflowAgentTools tools = new SqlWorkflowAgentTools(model, clickHouse, new ObjectMapper());

    JsonNode generated =
        new ObjectMapper()
            .readTree(
                tools
                    .generateSql(
                        "count by service",
                        null,
                        List.of(
                            new SqlWorkflowAgentTools.SchemaHint(
                                "db",
                                "events",
                                List.of(
                                    new SqlWorkflowAgentTools.SchemaColumn("service", "String")),
                                "",
                                "",
                                "MergeTree",
                                "")),
                        new SqlWorkflowAgentTools.SqlContext("db", "user"))
                    .block());
    assertThat(generated.path("validation").path("success").asBoolean()).isTrue();
    assertThat(generated.path("sql").asText()).contains("db.events");

    JsonNode optimized =
        new ObjectMapper()
            .readTree(
                tools
                    .optimizeSql(
                        generated.path("sql").asText(),
                        "latency",
                        Map.of("explain_index", Map.of("raw_text", "PrimaryKey")))
                    .block());
    assertThat(optimized.path("validation").path("success").asBoolean()).isTrue();
    assertThat(optimized.path("changes").path(0).asText()).contains("evidence");
    assertThat(model.calls()).isEqualTo(2);
    assertThat(model.lastToolCount()).isZero();
  }

  @Test
  void visualizationWhitelistsDeclarativeFieldsAndPreservesValidatedSql() throws Exception {
    ClickHouseAgentTools clickHouse = mock(ClickHouseAgentTools.class);
    when(clickHouse.validateSql(anyString())).thenReturn(Mono.just("{\"success\":true}"));
    ScriptedJsonModel model =
        new ScriptedJsonModel(
            """
            {"type":"line","title":"Requests over time","title_align":"center","width":20,
             "legend_placement":"bottom","legend_values":["min","max","javascript"],
             "value_format":"millisecond","html":"<script>alert(1)</script>"}
            """);
    SqlWorkflowAgentTools tools = new SqlWorkflowAgentTools(model, clickHouse, new ObjectMapper());

    JsonNode output =
        new ObjectMapper()
            .readTree(
                tools
                    .generateVisualization(
                        "line chart",
                        "SELECT event_time, count() FROM db.events GROUP BY event_time LIMIT 100")
                    .block());

    assertThat(output.path("type").asText()).isEqualTo("line");
    assertThat(output.path("width").asInt()).isEqualTo(12);
    assertThat(output.path("legendOption").path("values").toString())
        .isEqualTo("[\"min\",\"max\",\"sum\",\"count\"]");
    assertThat(output.path("datasource").path("sql").asText()).contains("db.events");
    assertThat(output.has("html")).isFalse();
  }

  @Test
  void visualizationNormalizesEveryLegendPlacementAndChartSpecificOptions() throws Exception {
    ClickHouseAgentTools clickHouse = mock(ClickHouseAgentTools.class);
    when(clickHouse.validateSql(anyString())).thenReturn(Mono.just("{\"success\":true}"));
    ScriptedJsonModel model =
        new ScriptedJsonModel(
            """
            {"type":"line","legend_placement":"inside","legend_values":[]}
            """,
            """
            {"type":"bar","legend_placement":"bottom","legend_values":["avg"]}
            """,
            """
            {"type":"area","legend_placement":"right","legend_values":["sum"],
             "y_axis":[{"min":0,"max":100,"min_interval":1}]}
            """,
            """
            {"type":"line","legend_placement":"none","legend_values":["min"]}
            """,
            """
            {"type":"pie","legend_placement":"none","legend_values":["min","max"],
             "label_show":false,"label_format":"name-value","value_format":"comma_number"}
            """);
    SqlWorkflowAgentTools tools = new SqlWorkflowAgentTools(model, clickHouse, new ObjectMapper());

    JsonNode inside = visualization(tools, "SELECT ts, value FROM db.metrics");
    assertThat(inside.path("legendOption").path("placement").asText()).isEqualTo("inside");
    assertThat(inside.path("legendOption").path("values")).isEmpty();

    JsonNode bottom =
        visualization(tools, "SELECT category, avg(value) FROM db.metrics GROUP BY category");
    assertThat(bottom.path("legendOption").path("placement").asText()).isEqualTo("bottom");
    assertThat(bottom.path("legendOption").path("values").toString())
        .isEqualTo("[\"min\",\"max\",\"avg\"]");

    JsonNode right = visualization(tools, "SELECT ts, sum(value) FROM db.metrics GROUP BY ts");
    assertThat(right.path("legendOption").path("placement").asText()).isEqualTo("right");
    assertThat(right.path("legendOption").path("values").toString())
        .isEqualTo("[\"min\",\"max\",\"sum\"]");
    assertThat(right.path("yAxis").path(0).path("min").asDouble()).isZero();
    assertThat(right.path("yAxis").path(0).path("max").asDouble()).isEqualTo(100);
    assertThat(right.path("yAxis").path(0).path("minInterval").asDouble()).isEqualTo(1);

    JsonNode hidden = visualization(tools, "SELECT ts, value FROM db.metrics");
    assertThat(hidden.path("legendOption").path("placement").asText()).isEqualTo("none");

    JsonNode pie =
        visualization(tools, "SELECT category, count() FROM db.metrics GROUP BY category");
    assertThat(pie.path("legendOption").path("placement").asText()).isEqualTo("inside");
    assertThat(pie.path("legendOption").path("values")).isEmpty();
    assertThat(pie.path("labelOption").path("show").asBoolean()).isFalse();
    assertThat(pie.path("labelOption").path("format").asText()).isEqualTo("name-value");
    assertThat(pie.path("valueFormat").asText()).isEqualTo("comma_number");
  }

  @Test
  void visualizationRejectsUnsupportedChartSpecificValues() throws Exception {
    ClickHouseAgentTools clickHouse = mock(ClickHouseAgentTools.class);
    when(clickHouse.validateSql(anyString())).thenReturn(Mono.just("{\"success\":true}"));
    ScriptedJsonModel model =
        new ScriptedJsonModel(
            """
            {"type":"pie","legend_placement":"right","label_format":"javascript",
             "value_format":"html","y_axis":[{"min":0}]}
            """);
    SqlWorkflowAgentTools tools = new SqlWorkflowAgentTools(model, clickHouse, new ObjectMapper());

    JsonNode output =
        visualization(tools, "SELECT category, count() FROM db.metrics GROUP BY category");
    assertThat(output.path("labelOption").path("format").asText()).isEqualTo("name-percent");
    assertThat(output.has("valueFormat")).isFalse();
    assertThat(output.has("yAxis")).isFalse();
  }

  private static JsonNode visualization(SqlWorkflowAgentTools tools, String sql) throws Exception {
    return new ObjectMapper().readTree(tools.generateVisualization("chart", sql).block());
  }

  private static final class ScriptedJsonModel implements Model {
    private final List<String> responses;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int lastToolCount;

    private ScriptedJsonModel(String... responses) {
      this.responses = List.of(responses);
    }

    @Override
    public Flux<ChatResponse> stream(
        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
      lastToolCount = tools.size();
      String response = responses.get(calls.getAndIncrement());
      return Flux.just(
          ChatResponse.builder()
              .content(List.of(TextBlock.builder().text(response).build()))
              .finishReason("stop")
              .build());
    }

    int calls() {
      return calls.get();
    }

    int lastToolCount() {
      return lastToolCount;
    }

    @Override
    public String getModelName() {
      return "scripted-json";
    }
  }
}
