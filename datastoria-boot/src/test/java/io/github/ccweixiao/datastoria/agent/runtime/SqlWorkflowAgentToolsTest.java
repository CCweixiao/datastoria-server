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
        .isEqualTo("[\"min\",\"max\"]");
    assertThat(output.path("datasource").path("sql").asText()).contains("db.events");
    assertThat(output.has("html")).isFalse();
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
