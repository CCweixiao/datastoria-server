package io.datastoria.server.clickhouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.runtime.ClickHouseAgentTools;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.service.ClickHouseConnectionService;

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
        new ClickHouseAgentTools(connectionService, connectionId, IDENTITY);
    assertThat(tools.getTables().block()).contains("datastoria_test", "query_events");
    assertThat(tools.exploreSchema("datastoria_test", "query_events").block())
        .contains("duration_ms", "UInt32");
    assertThat(
            tools
                .validateSql(
                    "SELECT service, count() FROM datastoria_test.query_events GROUP BY service")
                .block())
        .contains("SELECT");
    assertThat(tools.collectClusterStatus().block()).contains("active_parts");

    connectionService
        .query(connectionId, "SYSTEM FLUSH LOGS", Map.of("default_format", "JSON"), IDENTITY)
        .block();
    assertThat(tools.searchQueryLog("query_events", 10, 20).block()).contains("query_events");
  }
}
