package io.datastoria.server.agent.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.RunFailureCode;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.repository.AgentRunRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Taps an {@link AgentRunEvent} stream and persists the run's terminal status off the calling
 * thread, so blocking JDBC never runs on the Netty event loop:
 *
 * <ul>
 *   <li>{@link AgentRunEvent.UsageReported} → accumulates token usage across model calls.
 *   <li>{@link AgentRunEvent.RunCompleted} → {@code SUCCEEDED} with the accumulated usage JSON.
 *   <li>{@link AgentRunEvent.RunFailed} → {@code FAILED} with the sanitized {@link RunFailureCode}
 *       (never the raw provider message).
 * </ul>
 *
 * <p>Cancellation is handled separately by {@link RunCancellationPersister} (wired as the {@code
 * AgentRunService} cancellation observer), not here. Terminal transitions reuse the P4.3
 * optimistic-lock {@code revision} check, so a late cancel landing on an already-terminal run is a
 * safe no-op rather than an overwrite.
 *
 * <p>Persistence is fire-and-forget on a bounded scheduler: the SSE stream must not wait for, or be
 * blocked by, the DB write. A failed transition is logged (run id + status only — no prompt,
 * provider text, or credential is present on this path) and left for reconciliation.
 *
 * <p>AgentScope-free. The {@code usage} accumulator is safe because Reactor invokes {@code
 * doOnNext} sequentially per subscriber.
 */
public final class RunLifecycleRecorder {

  private static final Logger log = LoggerFactory.getLogger(RunLifecycleRecorder.class);

  private final AgentRunRepository runRepository;
  private final Scheduler jdbcScheduler;
  private final ObjectMapper mapper;

  public RunLifecycleRecorder(AgentRunRepository runRepository) {
    this(runRepository, Schedulers.boundedElastic(), new ObjectMapper());
  }

  public RunLifecycleRecorder(AgentRunRepository runRepository, Scheduler jdbcScheduler) {
    this(runRepository, jdbcScheduler, new ObjectMapper());
  }

  public RunLifecycleRecorder(
      AgentRunRepository runRepository, Scheduler jdbcScheduler, ObjectMapper mapper) {
    this.runRepository = runRepository;
    this.jdbcScheduler = jdbcScheduler;
    this.mapper = mapper;
  }

  /** Returns {@code events} unchanged, scheduling terminal persistence off the calling thread. */
  public Flux<AgentRunEvent> tap(String tenantId, String runId, Flux<AgentRunEvent> events) {
    long[] usage = {0, 0, 0}; // inputTokens, outputTokens, cachedTokens
    return events.doOnNext(
        e -> {
          if (e instanceof AgentRunEvent.UsageReported u) {
            usage[0] += u.usage().inputTokens();
            usage[1] += u.usage().outputTokens();
            usage[2] += u.usage().cachedTokens();
          } else if (e instanceof AgentRunEvent.RunCompleted) {
            dispatch(
                () ->
                    runRepository.transition(
                        tenantId,
                        runId,
                        AgentRunStatus.SUCCEEDED,
                        RunTransition.completing(Instant.now(), usageJson(usage))));
          } else if (e instanceof AgentRunEvent.RunFailed f) {
            RunFailureCode code = parseFailureCode(f.code());
            dispatch(
                () ->
                    runRepository.transition(
                        tenantId,
                        runId,
                        AgentRunStatus.FAILED,
                        RunTransition.failing(Instant.now(), code)));
          }
        });
  }

  private void dispatch(Runnable jdbcTask) {
    Mono.fromRunnable(
            () -> {
              try {
                jdbcTask.run();
              } catch (RuntimeException e) {
                // Optimistic-lock / illegal-transition outcomes (e.g. a late cancel already
                // terminated the run) are expected; log the class only — no sensitive payload.
                log.warn(
                    "Run terminal persistence did not apply: {}", e.getClass().getSimpleName());
              }
            })
        .subscribeOn(jdbcScheduler)
        .subscribe();
  }

  private String usageJson(long[] usage) {
    try {
      var node = mapper.createObjectNode();
      node.put("inputTokens", usage[0]);
      node.put("outputTokens", usage[1]);
      node.put("cachedTokens", usage[2]);
      node.put("totalTokens", usage[0] + usage[1]);
      return mapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private static RunFailureCode parseFailureCode(String code) {
    try {
      return RunFailureCode.valueOf(code);
    } catch (IllegalArgumentException | NullPointerException ignored) {
      return RunFailureCode.AGENT_INTERNAL;
    }
  }
}
