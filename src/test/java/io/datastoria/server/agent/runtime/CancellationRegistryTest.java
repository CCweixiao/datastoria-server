package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Verifies the {@link CancellationRegistry} cancel path (dispose subscription + interrupt agent)
 * and its tenant-isolation invariant: only the run owner can cancel; a mismatched requester is a
 * no-op.
 */
class CancellationRegistryTest {

  private static RunContext ctx(String runId, String tenant, String user) {
    return new RunContext(runId, tenant, user, "s", "m", "c", "a", "mc", Instant.EPOCH);
  }

  /** A RunnableAgent whose interrupt() is observable and whose stream is unused here. */
  private static RunnableAgent fakeAgent(AtomicBoolean interrupted) {
    return new RunnableAgent() {
      @Override
      public Flux<AgentRunEvent> streamEvents() {
        return Flux.empty();
      }

      @Override
      public void interrupt() {
        interrupted.set(true);
      }

      @Override
      public void close() {}
    };
  }

  private static Disposable neverSubscription() {
    return Flux.<AgentRunEvent>never().subscribe();
  }

  @Test
  void ownerCancelDisposesSubscriptionAndInterruptsAgent() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean interrupted = new AtomicBoolean();
    registry.register(ctx("run-1", "t1", "u1"), fakeAgent(interrupted));
    Disposable subscription = neverSubscription();
    registry.bindSubscription("run-1", subscription);

    assertThat(subscription.isDisposed()).isFalse();
    assertThat(registry.isActive("run-1")).isTrue();

    boolean result = registry.cancel("run-1", "t1", "u1");

    assertThat(result).isTrue();
    assertThat(subscription.isDisposed())
        .as("subscription disposed (stops provider tokens)")
        .isTrue();
    assertThat(interrupted).as("agent interrupted (cooperative cleanup)").isTrue();
  }

  @Test
  void wrongTenantCannotCancel() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean interrupted = new AtomicBoolean();
    registry.register(ctx("run-1", "t1", "u1"), fakeAgent(interrupted));
    Disposable subscription = neverSubscription();
    registry.bindSubscription("run-1", subscription);

    boolean result = registry.cancel("run-1", "t-other", "u1");

    assertThat(result).isFalse();
    assertThat(subscription.isDisposed()).isFalse();
    assertThat(interrupted).isFalse();
    assertThat(registry.isActive("run-1")).isTrue();
  }

  @Test
  void wrongUserCannotCancel() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean interrupted = new AtomicBoolean();
    registry.register(ctx("run-1", "t1", "u1"), fakeAgent(interrupted));
    Disposable subscription = neverSubscription();
    registry.bindSubscription("run-1", subscription);

    assertThat(registry.cancel("run-1", "t1", "u-other")).isFalse();
    assertThat(subscription.isDisposed()).isFalse();
    assertThat(interrupted).isFalse();
  }

  @Test
  void unknownRunReturnsFalse() {
    CancellationRegistry registry = new CancellationRegistry();
    assertThat(registry.cancel("nope", "t1", "u1")).isFalse();
  }

  @Test
  void cancelWithoutBoundSubscriptionStillInterrupts() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean interrupted = new AtomicBoolean();
    registry.register(ctx("run-1", "t1", "u1"), fakeAgent(interrupted));
    // No bindSubscription call — e.g. cancel arrived before subscribe completed.

    boolean result = registry.cancel("run-1", "t1", "u1");

    assertThat(result).isTrue();
    assertThat(interrupted).isTrue();
  }

  @Test
  void unregisterRemovesRun() {
    CancellationRegistry registry = new CancellationRegistry();
    registry.register(ctx("run-1", "t1", "u1"), fakeAgent(new AtomicBoolean()));
    assertThat(registry.isActive("run-1")).isTrue();
    registry.unregister("run-1");
    assertThat(registry.isActive("run-1")).isFalse();
  }
}
