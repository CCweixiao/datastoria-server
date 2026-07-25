package io.datastoria.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.PendingActionResolution;

/** Tenant and user scoped persistence contract for durable HITL actions. */
public interface AgentPendingActionRepository {

  AgentPendingAction create(String userId, AgentPendingAction action);

  Optional<AgentPendingAction> find(String tenantId, String userId, String runId, String actionId);

  List<AgentPendingAction> findPending(String tenantId, String userId, String runId);

  /**
   * Resolves with an optimistic CAS. An identical retry returns the original row; a different retry
   * throws {@code PendingActionConflictException}.
   */
  AgentPendingAction resolve(
      String tenantId,
      String userId,
      String runId,
      String actionId,
      PendingActionResolution resolution);

  /** Marks all due pending actions expired and returns the affected count. */
  int expireDue(Instant now);
}
