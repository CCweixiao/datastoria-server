package io.datastoria.server.agent.domain;

import java.time.Instant;

/**
 * Internal, runtime-agnostic event emitted by an agent run. This is DataStoria's unified event
 * contract (docs/design/api-contracts.md §7): it deliberately references NO AgentScope type so the
 * Agent runtime can be swapped without touching the event model, the (future) stream encoder, or
 * persistence. Only the {@code io.datastoria.server.agent.runtime} adapter layer ever touches
 * AgentScope {@code AgentEvent}; everything downstream consumes {@code AgentRunEvent}.
 *
 * <p>Every event is scoped to a single run ({@link #runId()}), carries a per-run monotonically
 * increasing {@link #sequence()}, and an {@link #occurredAt()} timestamp. P4 produces only the
 * text/reasoning/usage/lifecycle variants below; tool-related variants are deferred to P5, when
 * real tools arrive (P4 keeps the model-boundary tool schema empty — ADR-0004 §3.5).
 *
 * <p>The mapping from AgentScope events is fixed in ADR-0004 §3.2 (RunStarted ← AGENT_START,
 * ReasoningDelta ← THINKING_BLOCK_DELTA, TextDelta ← TEXT_BLOCK_DELTA, UsageReported ←
 * MODEL_CALL_END, RunCompleted ← AGENT_RESULT; onError ← RunFailed; dispose ← RunCancelled).
 */
public sealed interface AgentRunEvent
    permits AgentRunEvent.RunStarted,
        AgentRunEvent.TextBlockStarted,
        AgentRunEvent.TextDelta,
        AgentRunEvent.TextBlockEnded,
        AgentRunEvent.ReasoningBlockStarted,
        AgentRunEvent.ReasoningDelta,
        AgentRunEvent.ReasoningBlockEnded,
        AgentRunEvent.ToolInputStarted,
        AgentRunEvent.ToolInputDelta,
        AgentRunEvent.ToolInputAvailable,
        AgentRunEvent.ToolOutputStarted,
        AgentRunEvent.ToolOutputDelta,
        AgentRunEvent.ToolOutputAvailable,
        AgentRunEvent.ToolApprovalRequired,
        AgentRunEvent.QuestionRequired,
        AgentRunEvent.UsageReported,
        AgentRunEvent.RunCompleted,
        AgentRunEvent.RunFailed,
        AgentRunEvent.RunCancelled {

  /** Stable id of the run this event belongs to. */
  String runId();

  /** Per-run, monotonically increasing, strictly positive sequence number. */
  long sequence();

  /** When the event was produced (server clock). */
  Instant occurredAt();

  /** Emitted at AGENT_START; carries the owning session/message ids for traceability. */
  record RunStarted(
      String runId, long sequence, Instant occurredAt, String sessionId, String messageId)
      implements AgentRunEvent {}

  record TextBlockStarted(String runId, long sequence, Instant occurredAt)
      implements AgentRunEvent {}

  /** Incremental assistant text (TEXT_BLOCK_DELTA). Concatenated in order = full text. */
  record TextDelta(String runId, long sequence, Instant occurredAt, String delta)
      implements AgentRunEvent {}

  record TextBlockEnded(String runId, long sequence, Instant occurredAt) implements AgentRunEvent {}

  record ReasoningBlockStarted(String runId, long sequence, Instant occurredAt)
      implements AgentRunEvent {}

  /** Incremental reasoning / thinking (THINKING_BLOCK_DELTA). */
  record ReasoningDelta(String runId, long sequence, Instant occurredAt, String delta)
      implements AgentRunEvent {}

  record ReasoningBlockEnded(String runId, long sequence, Instant occurredAt)
      implements AgentRunEvent {}

  record ToolInputStarted(
      String runId, long sequence, Instant occurredAt, String toolCallId, String toolName)
      implements AgentRunEvent {}

  record ToolInputDelta(
      String runId,
      long sequence,
      Instant occurredAt,
      String toolCallId,
      String toolName,
      String delta)
      implements AgentRunEvent {}

  record ToolInputAvailable(
      String runId,
      long sequence,
      Instant occurredAt,
      String toolCallId,
      String toolName,
      String inputJson)
      implements AgentRunEvent {}

  record ToolOutputStarted(
      String runId, long sequence, Instant occurredAt, String toolCallId, String toolName)
      implements AgentRunEvent {}

  record ToolOutputDelta(
      String runId,
      long sequence,
      Instant occurredAt,
      String toolCallId,
      String toolName,
      String delta)
      implements AgentRunEvent {}

  record ToolOutputAvailable(
      String runId,
      long sequence,
      Instant occurredAt,
      String toolCallId,
      String toolName,
      String outputJson,
      boolean error,
      boolean denied)
      implements AgentRunEvent {}

  /** AgentScope permission ASK boundary. One event can contain a batch of gated calls. */
  record ToolApprovalRequired(
      String runId,
      long sequence,
      Instant occurredAt,
      String replyId,
      java.util.List<ToolApproval> approvals)
      implements AgentRunEvent {

    public ToolApprovalRequired {
      approvals = java.util.List.copyOf(approvals);
    }
  }

  record ToolApproval(String actionId, String toolCallId, String toolName, String inputJson) {}

  /** Server-side suspended question tool waiting for a durable user response. */
  record QuestionRequired(
      String runId,
      long sequence,
      Instant occurredAt,
      String replyId,
      String actionId,
      String toolCallId,
      String toolName,
      String inputJson)
      implements AgentRunEvent {}

  /** Token usage for a completed model call (MODEL_CALL_END). */
  record UsageReported(String runId, long sequence, Instant occurredAt, TokenUsage usage)
      implements AgentRunEvent {}

  /** Terminal success (AGENT_RESULT). Emitted exactly once. */
  record RunCompleted(String runId, long sequence, Instant occurredAt) implements AgentRunEvent {}

  /**
   * Terminal failure. {@code message} is a fixed, sanitized string keyed off {@code code}; it NEVER
   * carries the raw provider/tool error text, prompt, or credential (docs/design/harness-agent.md
   * §11, ADR-0004 §3.4).
   */
  record RunFailed(String runId, long sequence, Instant occurredAt, String code, String message)
      implements AgentRunEvent {}

  /** Terminal cancellation (subscription disposed / client disconnect). */
  record RunCancelled(String runId, long sequence, Instant occurredAt) implements AgentRunEvent {}
}
