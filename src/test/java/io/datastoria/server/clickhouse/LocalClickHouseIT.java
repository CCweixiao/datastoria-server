package io.datastoria.server.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.runtime.AgentToolExecutionPolicy;
import io.datastoria.server.agent.runtime.ClickHouseAgentTools;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.AuditLogRepository;
import io.datastoria.server.service.ClickHouseConnectionService;

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

  @Autowired
  @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER)
  Scheduler jdbcScheduler;

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

    connectionService
        .query(connectionId, "SYSTEM FLUSH LOGS", Map.of("default_format", "JSON"), IDENTITY)
        .block();
    assertThat(tools.searchQueryLog("query_events", 10, 20).block()).contains("query_events");
  }
}
