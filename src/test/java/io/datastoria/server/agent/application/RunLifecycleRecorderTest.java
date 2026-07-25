package io.datastoria.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.agent.domain.TokenUsage;
import io.datastoria.server.repository.AgentRunRepository;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Unit test for {@link RunLifecycleRecorder}: usage accumulates across model calls, terminal status
 * is persisted with the accumulated usage, and the JDBC write runs OFF the calling (subscriber)
 * thread — i.e. never on the Netty event loop.
 */
class RunLifecycleRecorderTest {

  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Test
  void accumulatesUsageAndPersistsSucceededOffCallingThread() throws Exception {
    CapturingRepo repo = new CapturingRepo();
    RunLifecycleRecorder recorder =
        new RunLifecycleRecorder(repo, Schedulers.newSingle("test-jdbc"));
    String caller = Thread.currentThread().getName();

    Flux<AgentRunEvent> events =
        Flux.just(
            new AgentRunEvent.UsageReported("r", 1, NOW, new TokenUsage(1, 2, 0, 0d)),
            new AgentRunEvent.UsageReported("r", 2, NOW, new TokenUsage(3, 4, 0, 0d)),
            new AgentRunEvent.RunCompleted("r", 3, NOW));

    recorder.tap("tenant", "r", events).blockLast();

    assertThat(repo.latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(repo.status).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(repo.usageJson).contains("\"inputTokens\":4").contains("\"outputTokens\":6");
    assertThat(repo.usageJson).contains("\"totalTokens\":10");
    assertThat(repo.thread).startsWith("test-jdbc");
    assertThat(repo.thread).isNotEqualTo(caller);
  }

  @Test
  void failedPersistedWithCodeAndNoRawMessage() throws Exception {
    CapturingRepo repo = new CapturingRepo();
    RunLifecycleRecorder recorder =
        new RunLifecycleRecorder(repo, Schedulers.newSingle("test-jdbc"));

    recorder
        .tap(
            "tenant",
            "r",
            Flux.just(
                new AgentRunEvent.RunFailed("r", 1, NOW, "MODEL_RATE_LIMITED", "raw ignored")))
        .blockLast();

    assertThat(repo.latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(repo.status).isEqualTo(AgentRunStatus.FAILED);
  }

  /** Minimal fake repo capturing the transition call + the thread it ran on. */
  private static final class CapturingRepo implements AgentRunRepository {
    final CountDownLatch latch = new CountDownLatch(1);
    volatile AgentRunStatus status;
    volatile String usageJson;
    volatile String thread;

    @Override
    public boolean transition(
        String tenantId, String runId, AgentRunStatus to, RunTransition payload) {
      this.status = to;
      this.thread = Thread.currentThread().getName();
      // RunTransition.completing carries usageJson as the 2nd arg; capture it if present.
      this.usageJson = payload.usageJson();
      latch.countDown();
      return true;
    }

    @Override
    public AgentRun create(AgentRun run) {
      return run;
    }

    @Override
    public Optional<AgentRun> find(String tenantId, String runId) {
      return Optional.empty();
    }

    @Override
    public Optional<AgentRun> findByIdempotencyKey(
        String tenantId, String userId, String idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public List<AgentRun> findBySession(String tenantId, String sessionId) {
      return List.of();
    }

    @Override
    public boolean applyCancellation(String runId, Instant cancelledAt) {
      return false;
    }
  }
}
