package io.datastoria.server.agent.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.model.ChatUsage;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.domain.RunFailureCode;
import io.datastoria.server.agent.domain.TokenUsage;

/**
 * Maps AgentScope {@link AgentEvent}s into DataStoria {@link AgentRunEvent}s per the fixed mapping
 * in ADR-0004 §3.2. Run-scoped and non-thread-safe by construction (Reactor invokes {@code map}
 * sequentially per subscriber), so an {@link AtomicLong} is used only to keep the sequence
 * monotonic under any upstream re-emit. Sequence numbers are assigned only to <em>emitted</em>
 * events; ignored AgentScope events (MODEL_CALL_START, AGENT_END, anything unknown) consume no
 * sequence number.
 */
public final class AgentEventMapper {

  private final RunContext context;
  private final Clock clock;
  private final AtomicLong sequence = new AtomicLong();

  public AgentEventMapper(RunContext context, Clock clock) {
    this.context = context;
    this.clock = clock;
  }

  /**
   * Maps one AgentScope event to zero or one internal events. Returns empty for events DataStoria
   * does not surface (MODEL_CALL_START, AGENT_END, and any future/unknown type) — they are silently
   * dropped here; the (future) stream encoder decides wire representation from the internal model.
   */
  public Optional<AgentRunEvent> toEvent(AgentEvent event) {
    AgentEventType type = event.getType();
    String runId = context.runId();
    switch (type) {
      case AGENT_START:
        return Optional.of(
            new AgentRunEvent.RunStarted(
                runId, seq(), now(), context.sessionId(), context.messageId()));
      case THINKING_BLOCK_START:
        return Optional.of(new AgentRunEvent.ReasoningBlockStarted(runId, seq(), now()));
      case THINKING_BLOCK_DELTA:
        return Optional.of(
            new AgentRunEvent.ReasoningDelta(
                runId, seq(), now(), ((ThinkingBlockDeltaEvent) event).getDelta()));
      case THINKING_BLOCK_END:
        return Optional.of(new AgentRunEvent.ReasoningBlockEnded(runId, seq(), now()));
      case TEXT_BLOCK_START:
        return Optional.of(new AgentRunEvent.TextBlockStarted(runId, seq(), now()));
      case TEXT_BLOCK_DELTA:
        return Optional.of(
            new AgentRunEvent.TextDelta(
                runId, seq(), now(), ((TextBlockDeltaEvent) event).getDelta()));
      case TEXT_BLOCK_END:
        return Optional.of(new AgentRunEvent.TextBlockEnded(runId, seq(), now()));
      case MODEL_CALL_END:
        return Optional.of(new AgentRunEvent.UsageReported(runId, seq(), now(), usageOf(event)));
      case AGENT_RESULT:
        return Optional.of(new AgentRunEvent.RunCompleted(runId, seq(), now()));
      default:
        // MODEL_CALL_START, AGENT_END, and unknown events carry nothing the wire needs.
        return Optional.empty();
    }
  }

  /**
   * Builds the terminal {@link AgentRunEvent.RunFailed} for a stream error. Called from the {@code
   * onErrorResume} boundary so the subscriber never sees {@code onError}; the raw cause is
   * classified into a safe code/message and never emitted.
   */
  public AgentRunEvent failure(Throwable error) {
    RunFailureCode code = classify(error);
    return new AgentRunEvent.RunFailed(
        context.runId(), seq(), now(), code.name(), code.safeMessage());
  }

  /** Builds the terminal cancellation event for the independent run-lifecycle observer. */
  public AgentRunEvent.RunCancelled cancelled() {
    return new AgentRunEvent.RunCancelled(context.runId(), seq(), now());
  }

  private long seq() {
    return sequence.incrementAndGet();
  }

  private Instant now() {
    return clock.instant();
  }

  private static TokenUsage usageOf(AgentEvent event) {
    ChatUsage usage = ((ModelCallEndEvent) event).getUsage();
    if (usage == null) {
      return TokenUsage.zero();
    }
    return new TokenUsage(
        usage.getInputTokens(), usage.getOutputTokens(), usage.getCachedTokens(), usage.getTime());
  }

  /**
   * Maps an exception to a sanitized failure code. Inspects the exception class name and message
   * purely to choose the code; the message is never emitted or logged verbatim, so sensitive text
   * (a leaked key or prompt fragment) cannot escape.
   */
  private static RunFailureCode classify(Throwable error) {
    if (error == null) {
      return RunFailureCode.AGENT_INTERNAL;
    }
    String className = error.getClass().getName();
    String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
    if (className.contains("RateLimit") || message.contains("rate limit")) {
      return RunFailureCode.MODEL_RATE_LIMITED;
    }
    if (className.contains("ContextLength")
        || message.contains("context length")
        || message.contains("too long")
        || message.contains("too many tokens")) {
      return RunFailureCode.MODEL_CONTEXT_TOO_LARGE;
    }
    if (className.contains("Timeout") || className.contains("Unreachable")) {
      return RunFailureCode.MODEL_UNAVAILABLE;
    }
    if (className.contains("MaxIter")
        || message.contains("max steps")
        || message.contains("max_iters")) {
      return RunFailureCode.AGENT_MAX_STEPS;
    }
    return RunFailureCode.AGENT_INTERNAL;
  }
}
