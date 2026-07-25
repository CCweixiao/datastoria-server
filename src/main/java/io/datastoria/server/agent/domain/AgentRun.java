package io.datastoria.server.agent.domain;

import java.time.Instant;

/**
 * One agent run row (docs/design/database-data-model.md §8). Persisted in {@code ds_agent_run};
 * every query/update is scoped by {@code tenantId}. {@code revision} is the optimistic-lock version
 * used by terminal transitions.
 *
 * <p>Holds NO prompt, API key, or provider credential. {@code inputSnapshotJson}/{@code usageJson}
 * carry only non-sensitive run metadata (pinned revision ids, token counts); provider errors are
 * reduced to {@code errorCode} (a {@link RunFailureCode} name) and a fixed {@code safeMessage}.
 */
public record AgentRun(
    String id,
    String tenantId,
    String userId,
    String sessionId,
    String messageId,
    String agentRevisionId,
    String modelId,
    AgentRunStatus status,
    String idempotencyKey,
    String requestId,
    String connectionId,
    String inputSnapshotJson,
    String usageJson,
    String errorCode,
    String safeMessage,
    long revision,
    Instant startedAt,
    Instant finishedAt,
    Instant createdAt,
    Instant updatedAt) {

  /** Convenience for the common RUNNING creation case. */
  public static AgentRun running(RunContext ctx, String agentRevisionId, String modelId) {
    Instant now = Instant.now();
    return new AgentRun(
        ctx.runId(),
        ctx.tenantId(),
        ctx.userId(),
        ctx.sessionId(),
        ctx.messageId(),
        agentRevisionId,
        modelId,
        AgentRunStatus.RUNNING,
        ctx.clientRequestId(),
        ctx.clientRequestId(),
        null,
        null,
        null,
        null,
        null,
        0L,
        now,
        null,
        now,
        now);
  }
}
