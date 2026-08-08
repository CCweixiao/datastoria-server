package io.github.ccweixiao.datastoria.service.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionResponse;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Mono;

class DdlSchemaInspectorTest {

  private static final Identity ADMIN = new Identity("tenant", "admin", Set.of("ROLE_ADMIN"));

  @Test
  void databaseExistenceIsCheckedAcrossEveryClusterReplica() {
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    when(connections.findById("connection", ADMIN)).thenReturn(Mono.just(connection("cluster_a")));
    when(connections.query(
            eq("connection"),
            contains("clusterAllReplicas({cluster:String}, system.databases)"),
            eq(Map.of("param_cluster", "cluster_a", "param_database", "demo")),
            eq(ADMIN)))
        .thenReturn(Mono.just("{\"data\":[[\"1\"]]}"));

    boolean exists =
        new DdlSchemaInspector(connections, new ObjectMapper())
            .databaseExists("connection", "demo", ADMIN)
            .block();

    assertThat(exists).isTrue();
    verify(connections)
        .query(
            eq("connection"),
            contains("clusterAllReplicas({cluster:String}, system.databases)"),
            eq(Map.of("param_cluster", "cluster_a", "param_database", "demo")),
            eq(ADMIN));
  }

  @Test
  void databaseExistenceCheckFailsClosedWhenClickHouseQueryFails() {
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    when(connections.findById("connection", ADMIN)).thenReturn(Mono.just(connection("cluster_a")));
    when(connections.query(
            eq("connection"),
            contains("clusterAllReplicas"),
            eq(Map.of("param_cluster", "cluster_a", "param_database", "demo")),
            eq(ADMIN)))
        .thenReturn(Mono.error(new IllegalStateException("metadata unavailable")));

    DdlSchemaInspector inspector = new DdlSchemaInspector(connections, new ObjectMapper());

    assertThatThrownBy(() -> inspector.databaseExists("connection", "demo", ADMIN).block())
        .hasMessageContaining("metadata unavailable");
  }

  private static ClickHouseConnectionResponse connection(String cluster) {
    Instant now = Instant.parse("2026-08-08T00:00:00Z");
    return new ClickHouseConnectionResponse(
        "connection",
        "Connection",
        "http://localhost:8123",
        "default",
        cluster,
        "default",
        true,
        "admin",
        true,
        1,
        now,
        now);
  }
}
