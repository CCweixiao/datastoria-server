package io.datastoria.server.agent.domain;

import java.time.Instant;

/**
 * Durable question/approval request. Request and response JSON are server-produced canonical JSON
 * and must never contain provider or connection credentials.
 */
public record AgentPendingAction(
    String id,
    String tenantId,
    String runId,
    String toolCallId,
    PendingActionType actionType,
    String requestJson,
    String responseJson,
    String resolutionDigest,
    PendingActionStatus status,
    Instant expiresAt,
    String resolvedBy,
    Instant resolvedAt,
    long revision,
    Instant createdAt,
    Instant updatedAt) {

  public AgentPendingAction {
    if (id == null
        || tenantId == null
        || runId == null
        || toolCallId == null
        || actionType == null
        || requestJson == null
        || status == null
        || expiresAt == null) {
      throw new IllegalArgumentException("Pending action required fields must not be null");
    }
    if (revision < 0) {
      throw new IllegalArgumentException("Pending action revision must be non-negative");
    }
    if (status == PendingActionStatus.PENDING
        && (responseJson != null
            || resolutionDigest != null
            || resolvedBy != null
            || resolvedAt != null)) {
      throw new IllegalArgumentException("Pending action cannot carry resolution fields");
    }
  }
}
