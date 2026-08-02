package io.github.ccweixiao.datastoria.agent.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatUsage;
import io.github.ccweixiao.datastoria.common.agent.AgentRunEvent;
import io.github.ccweixiao.datastoria.common.agent.RunContext;
import io.github.ccweixiao.datastoria.common.agent.RunFailureCode;
import io.github.ccweixiao.datastoria.common.agent.TokenUsage;

/**
 * Maps AgentScope {@link AgentEvent}s into DataStoria {@link AgentRunEvent}s per the fixed mapping
 * in {@code docs/api/stream-protocol.md}. Run-scoped and non-thread-safe by construction (Reactor
 * invokes {@code map} sequentially per subscriber), so an {@link AtomicLong} is used only to keep
 * the sequence monotonic under any upstream re-emit. Sequence numbers are assigned only to
 * <em>emitted</em> events; ignored AgentScope events (MODEL_CALL_START, AGENT_END, anything
 * unknown) consume no sequence number.
 */
public final class AgentEventMapper {

  private final RunContext context;
  private final Clock clock;
  private final AtomicLong sequence = new AtomicLong();
  private final boolean outputReasoning;
  private final ObjectMapper json =
      new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
  private final Map<String, StringBuilder> toolInputs = new ConcurrentHashMap<>();
  private final Map<String, StringBuilder> toolOutputs = new ConcurrentHashMap<>();

  public AgentEventMapper(RunContext context, Clock clock) {
    this(context, clock, 0L);
  }

  public AgentEventMapper(RunContext context, Clock clock, long initialSequence) {
    this(context, clock, initialSequence, true);
  }

  public AgentEventMapper(
      RunContext context, Clock clock, long initialSequence, boolean outputReasoning) {
    this.context = context;
    this.clock = clock;
    this.sequence.set(initialSequence);
    this.outputReasoning = outputReasoning;
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
        if (sequence.get() > 0) {
          return Optional.empty();
        }
        return Optional.of(
            new AgentRunEvent.RunStarted(
                runId, seq(), now(), context.sessionId(), context.messageId()));
      case THINKING_BLOCK_START:
        if (!outputReasoning) {
          return Optional.empty();
        }
        return Optional.of(new AgentRunEvent.ReasoningBlockStarted(runId, seq(), now()));
      case THINKING_BLOCK_DELTA:
        if (!outputReasoning) {
          return Optional.empty();
        }
        return Optional.of(
            new AgentRunEvent.ReasoningDelta(
                runId, seq(), now(), ((ThinkingBlockDeltaEvent) event).getDelta()));
      case THINKING_BLOCK_END:
        if (!outputReasoning) {
          return Optional.empty();
        }
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
      case TOOL_CALL_START:
        ToolCallStartEvent callStart = (ToolCallStartEvent) event;
        toolInputs.put(callStart.getToolCallId(), new StringBuilder());
        return Optional.of(
            new AgentRunEvent.ToolInputStarted(
                runId, seq(), now(), callStart.getToolCallId(), callStart.getToolCallName()));
      case TOOL_CALL_DELTA:
        ToolCallDeltaEvent callDelta = (ToolCallDeltaEvent) event;
        toolInputs
            .computeIfAbsent(callDelta.getToolCallId(), ignored -> new StringBuilder())
            .append(callDelta.getDelta());
        return Optional.of(
            new AgentRunEvent.ToolInputDelta(
                runId,
                seq(),
                now(),
                callDelta.getToolCallId(),
                callDelta.getToolCallName(),
                callDelta.getDelta()));
      case TOOL_CALL_END:
        ToolCallEndEvent callEnd = (ToolCallEndEvent) event;
        return Optional.of(
            new AgentRunEvent.ToolInputAvailable(
                runId,
                seq(),
                now(),
                callEnd.getToolCallId(),
                callEnd.getToolCallName(),
                normalizeJson(
                    toolInputs
                        .getOrDefault(callEnd.getToolCallId(), new StringBuilder("{}"))
                        .toString())));
      case TOOL_RESULT_START:
        ToolResultStartEvent resultStart = (ToolResultStartEvent) event;
        toolOutputs.put(resultStart.getToolCallId(), new StringBuilder());
        return Optional.of(
            new AgentRunEvent.ToolOutputStarted(
                runId, seq(), now(), resultStart.getToolCallId(), resultStart.getToolCallName()));
      case TOOL_RESULT_TEXT_DELTA:
        ToolResultTextDeltaEvent resultDelta = (ToolResultTextDeltaEvent) event;
        toolOutputs
            .computeIfAbsent(resultDelta.getToolCallId(), ignored -> new StringBuilder())
            .append(resultDelta.getDelta());
        return Optional.of(
            new AgentRunEvent.ToolOutputDelta(
                runId,
                seq(),
                now(),
                resultDelta.getToolCallId(),
                resultDelta.getToolCallName(),
                resultDelta.getDelta()));
      case TOOL_RESULT_END:
        ToolResultEndEvent resultEnd = (ToolResultEndEvent) event;
        ToolResultState state = resultEnd.getState();
        if (state == ToolResultState.RUNNING
            && "ask_user_question".equals(resultEnd.getToolCallName())) {
          String input =
              normalizeJson(
                  toolInputs
                      .getOrDefault(resultEnd.getToolCallId(), new StringBuilder("{}"))
                      .toString());
          return Optional.of(
              new AgentRunEvent.QuestionRequired(
                  runId,
                  seq(),
                  now(),
                  resultEnd.getReplyId(),
                  stableActionId(context.runId(), resultEnd.getToolCallId()),
                  resultEnd.getToolCallId(),
                  resultEnd.getToolCallName(),
                  input));
        }
        return Optional.of(
            new AgentRunEvent.ToolOutputAvailable(
                runId,
                seq(),
                now(),
                resultEnd.getToolCallId(),
                resultEnd.getToolCallName(),
                normalizeToolOutput(
                    toolOutputs
                        .getOrDefault(resultEnd.getToolCallId(), new StringBuilder("null"))
                        .toString()),
                state == ToolResultState.ERROR || state == ToolResultState.INTERRUPTED,
                state == ToolResultState.DENIED));
      case REQUIRE_USER_CONFIRM:
        RequireUserConfirmEvent confirm = (RequireUserConfirmEvent) event;
        return Optional.of(
            new AgentRunEvent.ToolApprovalRequired(
                runId,
                seq(),
                now(),
                confirm.getReplyId(),
                confirm.getToolCalls().stream().map(this::approval).toList()));
      case AGENT_RESULT:
        if (isPaused((AgentResultEvent) event)) {
          return Optional.empty();
        }
        return Optional.of(new AgentRunEvent.RunCompleted(runId, seq(), now()));
      default:
        // MODEL_CALL_START, AGENT_END, and unknown events carry nothing the wire needs.
        return Optional.empty();
    }
  }

  private boolean isPaused(AgentResultEvent event) {
    return switch (event.getResult().getGenerateReason()) {
      case TOOL_SUSPENDED,
          REASONING_STOP_REQUESTED,
          ACTING_STOP_REQUESTED,
          PERMISSION_ASKING,
          MIDDLEWARE_STOP_REQUESTED -> true;
      default -> false;
    };
  }

  private AgentRunEvent.ToolApproval approval(ToolUseBlock call) {
    return new AgentRunEvent.ToolApproval(
        stableActionId(context.runId(), call.getId()),
        call.getId(),
        call.getName(),
        writeJson(call.getInput()));
  }

  private String normalizeJson(String value) {
    if (value == null || value.isBlank()) {
      return "{}";
    }
    try {
      return json.writeValueAsString(json.readTree(value));
    } catch (Exception ignored) {
      return writeJson(value);
    }
  }

  /**
   * Normalises a tool's raw result text for the wire and for persistence. Any valid JSON (object,
   * array, or primitive) is preserved verbatim — so this never alters the contract of a tool that
   * returns structured JSON. Only free text is re-shaped: AgentScope absorbs a failed tool call
   * into a SUCCESS-state result block whose text is a free-form error string (e.g. {@code "Tool
   * execution failed: …"}), which would otherwise be serialised as a bare JSON string and crash
   * clients that destructure {@code output} as an object. Such text becomes {@code {"error": …}}.
   */
  private String normalizeToolOutput(String value) {
    if (value == null || value.isBlank()) {
      return "{}";
    }
    try {
      return writeJson(json.readTree(value));
    } catch (Exception parseFailure) {
      return writeJson(json.createObjectNode().put("error", value));
    }
  }

  private String writeJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception ignored) {
      return "null";
    }
  }

  private static String stableActionId(String runId, String toolCallId) {
    try {
      byte[] digest =
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(
                  (runId + "\n" + toolCallId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return "act_" + java.util.HexFormat.of().formatHex(digest, 0, 12);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
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
