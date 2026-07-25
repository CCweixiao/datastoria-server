package io.datastoria.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
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
import io.datastoria.server.domain.ChatMessage;
import io.datastoria.server.repository.AgentRunRepository;
import io.datastoria.server.repository.ChatMessageRepository;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Unit test for {@link RunLifecycleRecorder}: usage/text accumulate, terminal status is persisted
 * OFF the calling thread, and the assistant message is written on completion (but not on failure).
 */
class RunLifecycleRecorderTest {

  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Test
  void accumulatesUsageAndTextAndPersistsSucceededPlusAssistantMessageOffThread() throws Exception {
    CapturingRunRepo runRepo = new CapturingRunRepo();
    CapturingMessageRepo messageRepo = new CapturingMessageRepo();
    RunLifecycleRecorder recorder =
        new RunLifecycleRecorder(runRepo, messageRepo, Schedulers.newSingle("test-jdbc"));
    String caller = Thread.currentThread().getName();

    Flux<AgentRunEvent> events =
        Flux.just(
            new AgentRunEvent.UsageReported("r", 1, NOW, new TokenUsage(1, 2, 0, 0d)),
            new AgentRunEvent.UsageReported("r", 2, NOW, new TokenUsage(3, 4, 0, 0d)),
            new AgentRunEvent.TextDelta("r", 3, NOW, "Hello "),
            new AgentRunEvent.TextDelta("r", 4, NOW, "world"),
            new AgentRunEvent.RunCompleted("r", 5, NOW));

    recorder
        .tap(new RunMessageContext("tenant", "r", "user-1", "sess-1", "msg-1", "mdl-1"), events)
        .blockLast();

    assertThat(runRepo.latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(runRepo.status).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(runRepo.usageJson).contains("\"inputTokens\":4").contains("\"totalTokens\":10");
    assertThat(runRepo.thread).startsWith("test-jdbc");
    assertThat(runRepo.thread).isNotEqualTo(caller);

    assertThat(messageRepo.latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(messageRepo.saved).hasSize(1);
    ChatMessage msg = messageRepo.saved.get(0);
    assertThat(msg.role()).isEqualTo("assistant");
    assertThat(msg.id()).isEqualTo("msg-1");
    assertThat(msg.tenantId()).isEqualTo("tenant");
    assertThat(msg.sessionId()).isEqualTo("sess-1");
    assertThat(msg.partsJson()).contains("Hello world");
    assertThat(msg.metadataJson()).contains("\"usage\"").contains("\"inputTokens\":4");
  }

  @Test
  void failedRunPersistsNoAssistantMessage() throws Exception {
    CapturingRunRepo runRepo = new CapturingRunRepo();
    CapturingMessageRepo messageRepo = new CapturingMessageRepo();
    RunLifecycleRecorder recorder =
        new RunLifecycleRecorder(runRepo, messageRepo, Schedulers.newSingle("test-jdbc"));

    recorder
        .tap(
            new RunMessageContext("tenant", "r", "user-1", "sess-1", "msg-1", "mdl-1"),
            Flux.just(new AgentRunEvent.RunFailed("r", 1, NOW, "MODEL_RATE_LIMITED", "ignored")))
        .blockLast();

    assertThat(runRepo.latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(runRepo.status).isEqualTo(AgentRunStatus.FAILED);
    assertThat(messageRepo.saved).isEmpty(); // no hollow completed message on failure
  }

  private static final class CapturingRunRepo implements AgentRunRepository {
    final CountDownLatch latch = new CountDownLatch(1);
    volatile AgentRunStatus status;
    volatile String usageJson;
    volatile String thread;

    @Override
    public boolean transition(
        String tenantId, String runId, AgentRunStatus to, RunTransition payload) {
      this.status = to;
      this.thread = Thread.currentThread().getName();
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

  private static final class CapturingMessageRepo implements ChatMessageRepository {
    final List<ChatMessage> saved = new ArrayList<>();
    final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public ChatMessage save(ChatMessage message) {
      saved.add(message);
      latch.countDown();
      return message;
    }

    @Override
    public Optional<ChatMessage> findById(String id, String tenantId, String sessionId) {
      return Optional.empty();
    }

    @Override
    public List<ChatMessage> findBySession(String sessionId, String tenantId) {
      return List.of();
    }

    @Override
    public boolean exists(String tenantId, String userId, String sessionId, String messageId) {
      return false;
    }
  }
}
