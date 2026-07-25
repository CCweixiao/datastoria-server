package io.datastoria.server.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.runtime.AgentToolExecutionPolicy;
import io.datastoria.server.agent.runtime.ClickHouseAgentTools;
import io.datastoria.server.agent.testing.FakeModelAdapterProvider;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.AuditLogRepository;
import io.datastoria.server.service.ClickHouseConnectionService;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

/**
 * Opt-in smoke against the Docker-free local ClickHouse instance.
 *
 * <p>Run with:
 *
 * <pre>
 * tools/clickhouse/cluster.sh start
 * tools/clickhouse/cluster.sh seed
 * DATASTORIA_LOCAL_CLICKHOUSE=true ./mvnw -Dtest=LocalClickHouseIT test
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DATASTORIA_LOCAL_CLICKHOUSE", matches = "true")
@Import(LocalClickHouseIT.FakeModelConfig.class)
class LocalClickHouseIT {

  private static final String USER = "dev@example.com";
  private static final Identity IDENTITY =
      new Identity("tenant-test", USER, Set.of("ROLE_ADMIN", "ROLE_USER"));

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;
  @Autowired ClickHouseConnectionService connectionService;
  @Autowired ObjectMapper mapper;
  @Autowired AuditLogRepository auditLogRepository;
  @Autowired NamedParameterJdbcTemplate jdbc;
  @Autowired JdbcClient jdbcClient;
  @Autowired FakeModelAdapterProvider fakeProvider;

  @Autowired
  @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER)
  Scheduler jdbcScheduler;

  @TestConfiguration
  static class FakeModelConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    FakeModelAdapterProvider modelAdapterProvider() {
      return new FakeModelAdapterProvider();
    }
  }

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void browserApiAndAgentToolsReachRealLocalClickHouse() {
    JsonNode connection =
        web.post()
            .uri("/api/connections")
            .header("x-datastoria-user-email", USER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "docker-free-local",
                  "url": "http://127.0.0.1:18123",
                  "username": "default",
                  "password": "",
                  "enabled": true
                }
                """)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

    assertThat(connection).isNotNull();
    String connectionId = connection.path("id").asText();

    web.post()
        .uri("/api/connections/{id}/query", connectionId)
        .header("x-datastoria-user-email", USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {"query":"SELECT count() AS total FROM datastoria_test.query_events","parameters":{}}
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0].total")
        .isEqualTo(3);

    ClickHouseAgentTools tools =
        new ClickHouseAgentTools(
            connectionService,
            connectionId,
            IDENTITY,
            mapper,
            AgentToolExecutionPolicy.tracked(
                auditLogRepository, jdbcScheduler, IDENTITY, "local-it-run", connectionId));
    assertThat(tools.getTables("%events%", "datastoria_test", null, null, 20).block())
        .contains("datastoria_test", "query_events", "partition_key");
    assertThat(
            tools
                .exploreSchema(
                    java.util.List.of(
                        new ClickHouseAgentTools.SchemaTableRequest(
                            "datastoria_test.query_events", java.util.List.of())))
                .block())
        .contains("duration_ms", "UInt32", "sortingKey", "partitionBy");
    assertThat(
            tools
                .validateSql(
                    "SELECT service, count() FROM datastoria_test.query_events GROUP BY service")
                .block())
        .isEqualTo("{\"success\":true}");
    assertThat(tools.collectClusterStatus().block()).contains("active_parts");
    assertThatThrownBy(
            () ->
                new ClickHouseAgentTools(
                        connectionService,
                        connectionId,
                        new Identity("tenant-test", "other@example.com", Set.of()),
                        mapper)
                    .getTables("%events%", "datastoria_test", null, null, 20)
                    .block())
        .hasMessageContaining("ClickHouseConnection");

    String wideColumns =
        IntStream.range(0, 105)
            .mapToObj(index -> "c" + index + " UInt8")
            .collect(java.util.stream.Collectors.joining(","));
    connectionService
        .query(
            connectionId, "DROP TABLE IF EXISTS datastoria_test.p6_wide_schema", Map.of(), IDENTITY)
        .then(
            connectionService.query(
                connectionId,
                "CREATE TABLE datastoria_test.p6_wide_schema ("
                    + wideColumns
                    + ") ENGINE=MergeTree ORDER BY tuple()",
                Map.of(),
                IDENTITY))
        .block();
    JsonNode wideSchema;
    try {
      wideSchema =
          mapper.readTree(
              tools
                  .exploreSchema(
                      java.util.List.of(
                          new ClickHouseAgentTools.SchemaTableRequest(
                              "datastoria_test.p6_wide_schema", java.util.List.of())))
                  .block());
    } catch (java.io.IOException error) {
      throw new AssertionError(error);
    } finally {
      connectionService
          .query(
              connectionId,
              "DROP TABLE IF EXISTS datastoria_test.p6_wide_schema",
              Map.of(),
              IDENTITY)
          .block();
    }
    assertThat(wideSchema.path(0).path("totalColumns").asInt()).isEqualTo(105);
    assertThat(wideSchema.path(0).path("columns").size()).isEqualTo(100);
    assertThat(wideSchema.path(0).path("truncated").asBoolean()).isTrue();

    Integer audited =
        jdbc.queryForObject(
            "SELECT count(*) FROM ds_audit_log WHERE resource_id = :runId"
                + " AND action = 'agent.tool.execute'",
            Map.of("runId", "local-it-run"),
            Integer.class);
    assertThat(audited).isGreaterThanOrEqualTo(4);

    seedAgentRecords(connectionId);
    ReadonlyToolChainModel toolChainModel = new ReadonlyToolChainModel();
    fakeProvider.setModel(toolChainModel);
    String sse =
        web.post()
            .uri("/api/ai/agent")
            .header("x-datastoria-user-email", USER)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                "{\"sessionId\":\"local-tool-session\",\"connectionId\":\""
                    + connectionId
                    + "\",\"message\":{\"id\":\"local-msg\",\"role\":\"user\",\"parts\":["
                    + "{\"type\":\"text\",\"text\":\"inspect query_events\"}]},"
                    + "\"modelConfigId\":\"local-model\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertThat(sse).contains("readonly tools complete", "data: [DONE]");
    assertThat(toolChainModel.outputs()).contains("query_events", "duration_ms", "success", "true");

    connectionService
        .query(connectionId, "SYSTEM FLUSH LOGS", Map.of("default_format", "JSON"), IDENTITY)
        .block();
    assertThat(tools.searchQueryLog("query_events", 10, 20).block()).contains("query_events");
  }

  private void seedAgentRecords(String connectionId) {
    String now = Instant.now().toString();
    jdbcClient
        .sql(
            "INSERT INTO ds_model_provider"
                + " (id, tenant_id, provider_key, display_name, auth_type, enabled, created_by,"
                + " updated_by, created_at, updated_at)"
                + " VALUES ('local-provider','tenant-test','openai','Local','api_key',1,"
                + " 'admin','admin',:now,:now)")
        .param("now", now)
        .update();
    jdbcClient
        .sql(
            "INSERT INTO ds_model"
                + " (id, tenant_id, provider_id, model_key, display_name, source, enabled,"
                + " created_at, updated_at)"
                + " VALUES ('local-model','tenant-test','local-provider','mock','Mock','system',"
                + " 1,:now,:now)")
        .param("now", now)
        .update();
    jdbcClient
        .sql(
            "INSERT INTO ds_chat_session"
                + " (id, tenant_id, user_id, connection_id, title, revision, created_at, updated_at)"
                + " VALUES ('local-tool-session','tenant-test',:user,:connection,'tools',0,:now,:now)")
        .param("user", USER)
        .param("connection", connectionId)
        .param("now", now)
        .update();
  }

  private static final class ReadonlyToolChainModel implements Model {
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String outputs = "";

    @Override
    public Flux<ChatResponse> stream(
        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
      outputs =
          messages.stream()
              .flatMap(message -> message.getContent().stream())
              .filter(ToolResultBlock.class::isInstance)
              .map(ToolResultBlock.class::cast)
              .flatMap(result -> result.getOutput().stream())
              .filter(TextBlock.class::isInstance)
              .map(TextBlock.class::cast)
              .map(TextBlock::getText)
              .reduce("", (left, right) -> left + right);
      int call = calls.incrementAndGet();
      if (call <= 3) {
        String name =
            switch (call) {
              case 1 -> "get_tables";
              case 2 -> "explore_schema";
              default -> "validate_sql";
            };
        assertThat(tools.stream().map(ToolSchema::getName)).contains(name);
        String input =
            switch (call) {
              case 1 -> "{\"name_pattern\":\"%events%\",\"database\":\"datastoria_test\",\"limit\":20}";
              case 2 -> "{\"tables\":[{\"table\":\"datastoria_test.query_events\","
                  + "\"columns\":[\"duration_ms\"]}]}";
              default -> "{\"sql\":\"SELECT duration_ms FROM datastoria_test.query_events LIMIT 1\"}";
            };
        ToolUseBlock toolCall =
            ToolUseBlock.builder()
                .id("p6-tool-" + call)
                .name(name)
                .content(input)
                .state(ToolCallState.FINISHED)
                .build();
        return Flux.just(
            ChatResponse.builder()
                .content(List.of(toolCall))
                .finishReason("tool_calls")
                .metadata(Map.of())
                .build());
      }
      ChatUsage usage = ChatUsage.builder().inputTokens(3).outputTokens(3).time(0.0).build();
      return Flux.just(
          ChatResponse.builder()
              .content(List.of(TextBlock.builder().text("readonly tools complete").build()))
              .build(),
          ChatResponse.builder()
              .content(List.of())
              .usage(usage)
              .finishReason("stop")
              .metadata(Map.of())
              .build());
    }

    @Override
    public String getModelName() {
      return "p6-readonly-tool-chain";
    }

    String outputs() {
      return outputs;
    }
  }
}
