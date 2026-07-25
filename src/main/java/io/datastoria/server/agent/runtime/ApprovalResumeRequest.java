package io.datastoria.server.agent.runtime;

import java.util.List;
import java.util.Map;

/** AgentScope-free description of one persisted permission checkpoint and its user decisions. */
public record ApprovalResumeRequest(
    long checkpointSequence, String replyId, List<Decision> decisions) {

  public ApprovalResumeRequest {
    if (checkpointSequence <= 0 || replyId == null || decisions == null || decisions.isEmpty()) {
      throw new IllegalArgumentException("Approval resume request is incomplete");
    }
    decisions = List.copyOf(decisions);
  }

  public record Decision(
      String toolCallId, String toolName, Map<String, Object> input, boolean confirmed) {

    public Decision {
      if (toolCallId == null || toolName == null) {
        throw new IllegalArgumentException("Approval decision must identify the tool call");
      }
      input = input == null ? Map.of() : Map.copyOf(input);
    }
  }
}
