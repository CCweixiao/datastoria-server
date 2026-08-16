package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.ClickHouseConnection;
import io.github.ccweixiao.datastoria.common.error.ProviderOperationException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ClickHouseConnectionRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/**
 * Verifies the metadata probe's failure isolation: identity facts still resolve when the
 * version-dependent capability query is rejected, degrading every probed flag to its conservative
 * default instead of failing the whole request.
 */
class ClickHouseConnectionMetadataServiceTest {

  private static final Identity IDENTITY =
      new Identity("tenant-test", "dev@example.com", Set.of("ROLE_USER"));

  private static final String CORE_JSON =
      "{\"data\":[[\"readonly\",\"UTC\",\"node-1\",\"node-1.example.com\",\"24.8.1.1\"]]}";
  private static final String CAPABILITY_JSON =
      "{\"data\":[[true,true,true,true,true,true,true,false]]}";
  private static final String TOPOLOGY_JSON =
      "{\"data\":[[\"prod\",\"node-1\",\"10.0.0.1\",9000,1,1,1]]}";
  private static final String EVENTS_JSON = "{\"data\":[[\"Query\"],[\"Merge\"]]}";

  @Test
  void capabilityProbeFailureDegradesInsteadOfFailingMetadata() {
    ClickHouseConnectionMetadataService service =
        service(
            (connection, password, query) ->
                query.contains("hasColumnInTable")
                    ? Mono.error(
                        new ProviderOperationException(
                            "CLICKHOUSE_QUERY_FAILED", 403, "system.columns is not allowed"))
                    : canned(query));

    StepVerifier.create(service.get("conn-1", IDENTITY))
        .assertNext(
            metadata -> {
              assertThat(metadata.serverVersion()).isEqualTo("24.8.1.1");
              assertThat(metadata.internalUser()).isEqualTo("readonly");
              // Conservative defaults: every probed feature is treated as absent.
              assertThat(metadata.queryLogTableHasHostnameColumn()).isFalse();
              assertThat(metadata.hasFormatQueryFunction()).isFalse();
              assertThat(metadata.functionTableHasDescriptionColumn()).isFalse();
              assertThat(metadata.spanLogTableHasHostnameColumn()).isFalse();
              assertThat(metadata.partLogTableHasNodeNameColumn()).isFalse();
              assertThat(metadata.readonlySkipUnavailableShards()).isFalse();
              // Unrelated probes still resolve.
              assertThat(metadata.detectedCluster()).isEqualTo("prod");
              assertThat(metadata.profileEvents()).containsExactly("Query", "Merge");
            })
        .verifyComplete();
  }

  @Test
  void capabilityProbeSuccessMapsEveryFlag() {
    ClickHouseConnectionMetadataService service =
        service((connection, password, query) -> canned(query));

    StepVerifier.create(service.get("conn-1", IDENTITY))
        .assertNext(
            metadata -> {
              assertThat(metadata.serverVersion()).isEqualTo("24.8.1.1");
              assertThat(metadata.functionTableHasDescriptionColumn()).isTrue();
              assertThat(metadata.hasFormatQueryFunction()).isTrue();
              assertThat(metadata.queryLogTableHasHostnameColumn()).isTrue();
              assertThat(metadata.spanLogTableHasHostnameColumn()).isTrue();
              assertThat(metadata.partLogTableHasNodeNameColumn()).isTrue();
              assertThat(metadata.readonlySkipUnavailableShards()).isFalse();
              assertThat(metadata.clusterNodes()).hasSize(1);
            })
        .verifyComplete();
  }

  @Test
  void coreQueryFailureStillFailsMetadata() {
    ClickHouseConnectionMetadataService service =
        service(
            (connection, password, query) ->
                query.contains("currentUser()")
                    ? Mono.error(
                        new ProviderOperationException("CLICKHOUSE_UNAVAILABLE", 502, "down"))
                    : canned(query));

    StepVerifier.create(service.get("conn-1", IDENTITY))
        .expectError(ProviderOperationException.class)
        .verify();
  }

  private static Mono<String> canned(String query) {
    if (query.contains("currentUser()")) {
      return Mono.just(CORE_JSON);
    }
    if (query.contains("hasColumnInTable")) {
      return Mono.just(CAPABILITY_JSON);
    }
    if (query.contains("system.clusters")) {
      return Mono.just(TOPOLOGY_JSON);
    }
    return Mono.just(EVENTS_JSON);
  }

  private static ClickHouseConnectionMetadataService service(StubRemote remote) {
    ClickHouseConnectionRepository repository =
        new ClickHouseConnectionRepository() {
          @Override
          public ClickHouseConnection save(ClickHouseConnection connection) {
            return connection;
          }

          @Override
          public Optional<ClickHouseConnection> findById(String id, String tenantId) {
            return Optional.of(connection());
          }

          @Override
          public java.util.List<ClickHouseConnection> findAll(String tenantId) {
            return java.util.List.of();
          }

          @Override
          public ClickHouseConnection update(
              ClickHouseConnection connection, long expectedRevision) {
            return connection;
          }

          @Override
          public void softDelete(String id, String tenantId, long expectedRevision) {}
        };
    return new ClickHouseConnectionMetadataService(
        repository,
        null,
        new StubRemoteClient(remote),
        new ObjectMapper(),
        Schedulers.boundedElastic(),
        Duration.ofMinutes(5),
        100);
  }

  private static ClickHouseConnection connection() {
    return new ClickHouseConnection(
        "conn-1",
        "tenant-test",
        "dev@example.com",
        "prod",
        "http://localhost:8123",
        "readonly",
        null,
        null,
        null,
        null,
        null,
        null,
        true,
        0,
        null,
        null,
        null);
  }

  private interface StubRemote {
    Mono<String> execute(ClickHouseConnection connection, String password, String query);
  }

  /** Routes stub answers through the real client type the service depends on. */
  private static final class StubRemoteClient extends ClickHouseRemoteClient {

    private final StubRemote remote;

    StubRemoteClient(StubRemote remote) {
      super(org.springframework.web.reactive.function.client.WebClient.builder());
      this.remote = remote;
    }

    @Override
    public Mono<String> execute(ClickHouseConnection connection, String password, String query) {
      return remote.execute(connection, password, query);
    }

    @Override
    public Mono<String> execute(
        ClickHouseConnection connection,
        String password,
        String query,
        Map<String, Object> parameters) {
      return remote.execute(connection, password, query);
    }
  }
}
