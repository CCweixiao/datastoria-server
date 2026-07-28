package io.github.ccweixiao.datastoria.common.agent;

import java.util.List;

/**
 * DataStoria-owned recovery payload at a permission pause. It contains only tool identifiers,
 * names, and already-redacted JSON inputs; no provider/model credential or AgentScope type.
 */
public record PendingActionCheckpoint(String replyId, List<PendingToolCall> toolCalls) {

  public PendingActionCheckpoint {
    if (replyId == null || toolCalls == null || toolCalls.isEmpty()) {
      throw new IllegalArgumentException("Pending action checkpoint must contain tool calls");
    }
    toolCalls = List.copyOf(toolCalls);
  }

  public record PendingToolCall(
      String actionId, String toolCallId, String toolName, String inputJson) {}
}
