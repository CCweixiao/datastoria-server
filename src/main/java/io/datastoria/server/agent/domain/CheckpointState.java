package io.datastoria.server.agent.domain;

import java.util.Map;

/**
 * DataStoria-owned, safe-to-persist subset of an agent's run state — what the checkpoint codec
 * serializes. It deliberately captures only the agent's <em>control</em> scalars (progress,
 * summary, ids) and never the conversation {@code context} (which carries the user prompt and
 * message history). Conversation replay comes from {@code ds_chat_message}; the checkpoint exists
 * to resume agent execution (docs/design/harness-agent.md §10).
 *
 * <p>Holds NO API key, provider credential, or secret. The codec additionally redacts any sensitive
 * key that ever appears in {@code metadata} as defense-in-depth. AgentScope-free.
 */
public record CheckpointState(
    String sessionId,
    String userId,
    String replyId,
    int currentIteration,
    String summary,
    boolean shutdownInterrupted,
    Map<String, Object> metadata) {

  public CheckpointState {
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
  }
}
