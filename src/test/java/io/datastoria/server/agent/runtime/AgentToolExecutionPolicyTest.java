package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.datastoria.server.api.error.ProviderOperationException;
import io.datastoria.server.domain.AuditLog;
import io.datastoria.server.identity.Identity;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class AgentToolExecutionPolicyTest {

  @Test
  void rejectsOversizedOutputAndAuditsOnlySafeMetadata() {
    AtomicReference<AuditLog> saved = new AtomicReference<>();
    AgentToolExecutionPolicy policy =
        AgentToolExecutionPolicy.tracked(
            entry -> {
              saved.set(entry);
              return entry;
            },
            Schedulers.immediate(),
            new Identity("tenant-a", "user-a", Set.of()),
            "run-a",
            "connection-a");

    assertThatThrownBy(
            () ->
                policy
                    .guard(
                        "explore_schema",
                        Mono.just("x".repeat(AgentToolExecutionPolicy.MAX_OUTPUT_CHARS + 1)))
                    .block())
        .isInstanceOf(ProviderOperationException.class)
        .hasMessageContaining("exceeded the server limit");
    assertThat(saved.get().safeDiff())
        .isEqualTo("{\"tool\":\"explore_schema\",\"connectionId\":\"connection-a\"}");
    assertThat(saved.get().result()).isEqualTo("failure");
  }

  @Test
  void rejectsThirdConcurrentCallAndReleasesPermitsOnCancellation() {
    AgentToolExecutionPolicy policy = AgentToolExecutionPolicy.untracked();
    Disposable first = policy.guard("get_tables", Mono.never()).subscribe();
    Disposable second = policy.guard("explore_schema", Mono.never()).subscribe();

    assertThatThrownBy(() -> policy.guard("validate_sql", Mono.just("unused")).block())
        .isInstanceOf(ProviderOperationException.class)
        .hasMessageContaining("Too many concurrent");

    first.dispose();
    second.dispose();
    assertThat(policy.guard("validate_sql", Mono.just("ok")).block()).isEqualTo("ok");
  }

  @Test
  void mapsToolTimeoutWithoutWaitingForRemoteClientTimeout() {
    AgentToolExecutionPolicy policy = AgentToolExecutionPolicy.untracked(Duration.ofMillis(10));

    assertThatThrownBy(
            () ->
                policy
                    .guard("get_tables", Mono.delay(Duration.ofSeconds(1)).thenReturn("late"))
                    .block())
        .isInstanceOf(ProviderOperationException.class)
        .hasMessageContaining("timed out");
  }
}
