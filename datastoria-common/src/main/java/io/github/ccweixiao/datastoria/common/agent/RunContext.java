package io.github.ccweixiao.datastoria.common.agent;

import java.time.Instant;
import java.util.Objects;

/**
 * Resolved, run-scoped context for a single agent run (docs/design/harness-agent.md §3). Every
 * field is server-resolved: {@code tenantId}/{@code userId} come from the authenticated {@code
 * Identity} (never client-supplied), {@code agentRevisionId}/{@code modelConfigId} pin the
 * immutable revisions used for this run, and {@code clientRequestId} is the idempotency key.
 *
 * <p>Holds NO secrets. Provider credentials are resolved inside the {@code ModelAdapter} and
 * injected into AgentScope {@code GenerateOptions} server-side; they are never carried in this
 * object, the request body, or the response ({@code docs/security/secrets.md}).
 */
public record RunContext(
    String runId,
    String tenantId,
    String userId,
    String sessionId,
    String messageId,
    String clientRequestId,
    String agentRevisionId,
    String modelConfigId,
    Instant createdAt) {

  public RunContext {
    Objects.requireNonNull(runId, "runId");
    Objects.requireNonNull(tenantId, "tenantId");
    Objects.requireNonNull(userId, "userId");
    sessionId = sessionId == null ? "" : sessionId;
    messageId = messageId == null ? "" : messageId;
    clientRequestId = clientRequestId == null ? "" : clientRequestId;
    agentRevisionId = agentRevisionId == null ? "" : agentRevisionId;
    modelConfigId = modelConfigId == null ? "" : modelConfigId;
    createdAt = createdAt == null ? Instant.EPOCH : createdAt;
  }
}
