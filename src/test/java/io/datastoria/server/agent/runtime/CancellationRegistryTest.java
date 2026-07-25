package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;

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
      public AgentRunEvent.RunCancelled cancelledEvent() {
        return new AgentRunEvent.RunCancelled("run", 1, Instant.EPOCH);
      }

      @Override
      public void interrupt() {
        interrupted.set(true);
      }

      @Override
      public void close() {}
    };
  }

  private static final class TestSubscription implements Subscription {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    @Override
    public void request(long count) {}

    @Override
    public void cancel() {
      cancelled.set(true);
    }

    boolean isCancelled() {
      return cancelled.get();
    }
  }

  @Test
  void ownerCancelDisposesSubscriptionAndInterruptsAgent() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean interrupted = new AtomicBoolean();
    TestSubscription subscription = new TestSubscription();
    RunnableAgent agent = fakeAgent(interrupted);
    registry.register(ctx("run-1", "t1", "u1"), agent);
    registry.bindSubscription("run-1", agent, subscription);

    assertThat(subscription.isCancelled()).isFalse();
    assertThat(registry.isActive("run-1")).isTrue();

    boolean result = registry.cancel("run-1", "t1", "u1");

    assertThat(result).isTrue();
    assertThat(subscription.isCancelled())
        .as("subscription disposed (stops provider tokens)")
        .isTrue();
    assertThat(interrupted).as("agent interrupted (cooperative cleanup)").isTrue();
  }

  @Test
  void wrongTenantCannotCancel() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean interrupted = new AtomicBoolean();
    RunnableAgent agent = fakeAgent(interrupted);
    registry.register(ctx("run-1", "t1", "u1"), agent);
    TestSubscription subscription = new TestSubscription();
    registry.bindSubscription("run-1", agent, subscription);

    boolean result = registry.cancel("run-1", "t-other", "u1");

    assertThat(result).isFalse();
    assertThat(subscription.isCancelled()).isFalse();
    assertThat(interrupted).isFalse();
    assertThat(registry.isActive("run-1")).isTrue();
  }

  @Test
  void wrongUserCannotCancel() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean interrupted = new AtomicBoolean();
    RunnableAgent agent = fakeAgent(interrupted);
    registry.register(ctx("run-1", "t1", "u1"), agent);
    TestSubscription subscription = new TestSubscription();
    registry.bindSubscription("run-1", agent, subscription);

    assertThat(registry.cancel("run-1", "t1", "u-other")).isFalse();
    assertThat(subscription.isCancelled()).isFalse();
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
  void subscriptionBoundAfterCancelIsCancelledImmediately() {
    CancellationRegistry registry = new CancellationRegistry();
    RunnableAgent agent = fakeAgent(new AtomicBoolean());
    registry.register(ctx("run-1", "t1", "u1"), agent);
    assertThat(registry.cancel("run-1", "t1", "u1")).isTrue();

    TestSubscription lateSubscription = new TestSubscription();
    registry.bindSubscription("run-1", agent, lateSubscription);

    assertThat(lateSubscription.isCancelled()).isTrue();
  }

  @Test
  void duplicateRunIdIsRejectedWithoutReplacingExistingRegistration() {
    CancellationRegistry registry = new CancellationRegistry();
    AtomicBoolean firstInterrupted = new AtomicBoolean();
    AtomicBoolean duplicateInterrupted = new AtomicBoolean();
    RunnableAgent first = fakeAgent(firstInterrupted);
    RunnableAgent duplicate = fakeAgent(duplicateInterrupted);

    assertThat(registry.register(ctx("run-1", "t1", "u1"), first)).isTrue();
    assertThat(registry.register(ctx("run-1", "t1", "u1"), duplicate)).isFalse();
    assertThat(registry.cancel("run-1", "t1", "u1")).isTrue();

    assertThat(firstInterrupted).isTrue();
    assertThat(duplicateInterrupted).isFalse();
  }

  @Test
  void unregisterRemovesRun() {
    CancellationRegistry registry = new CancellationRegistry();
    RunnableAgent agent = fakeAgent(new AtomicBoolean());
    registry.register(ctx("run-1", "t1", "u1"), agent);
    assertThat(registry.isActive("run-1")).isTrue();
    registry.unregister("run-1", agent);
    assertThat(registry.isActive("run-1")).isFalse();
  }
}
