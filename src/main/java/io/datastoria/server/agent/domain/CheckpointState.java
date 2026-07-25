package io.datastoria.server.agent.domain;

/**
 * DataStoria-owned, safe-to-persist subset of an agent's run state — what the checkpoint codec
 * serializes. It deliberately captures only identifiers, counters and flags. Free-form fields such
 * as conversation {@code context}, summary and metadata are excluded because all can contain user
 * prompts, tool output or credentials. Conversation replay comes from {@code ds_chat_message}; the
 * checkpoint exists to resume agent execution (docs/design/harness-agent.md §10).
 *
 * <p>Holds NO API key, provider credential, prompt, summary or arbitrary metadata. AgentScope-free.
 */
public record CheckpointState(
    String sessionId,
    String userId,
    String replyId,
    int currentIteration,
    boolean shutdownInterrupted) {}
