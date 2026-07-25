package io.datastoria.server.agent.runtime;

import java.util.Map;

/** AgentScope-free description of a persisted question checkpoint and its user response. */
public record QuestionResumeRequest(
    long checkpointSequence,
    String replyId,
    String actionId,
    String toolCallId,
    String toolName,
    Map<String, Object> input,
    String responseJson) {

  public QuestionResumeRequest {
    if (checkpointSequence <= 0
        || replyId == null
        || actionId == null
        || toolCallId == null
        || toolName == null
        || responseJson == null) {
      throw new IllegalArgumentException("Question resume request is incomplete");
    }
    input = input == null ? Map.of() : Map.copyOf(input);
  }
}
