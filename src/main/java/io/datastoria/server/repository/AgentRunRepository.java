package io.datastoria.server.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.RunTransition;

/**
 * Persistent access to {@code ds_agent_run}. Every method is scoped by {@code tenantId} (and where
 * relevant {@code userId}); tenant isolation is enforced here, not only at the controller. {@link
 * #transition} and {@link #applyCancellation} implement the run state machine with optimistic
 * locking so concurrent terminal transitions never overwrite each other.
 */
public interface AgentRunRepository {

  /**
   * Inserts a new run row. The caller resolves the idempotency key via {@link
   * #findByIdempotencyKey}.
   */
  AgentRun create(AgentRun run);

  /** Looks up a run under (tenantId, runId). Empty when missing or owned by another tenant. */
  Optional<AgentRun> find(String tenantId, String runId);

  /**
   * Resolves an existing run by idempotency key under (tenantId, userId); the dedup path for P4.6.
   */
  Optional<AgentRun> findByIdempotencyKey(String tenantId, String userId, String idempotencyKey);

  /** Runs for a session, oldest first. */
  List<AgentRun> findBySession(String tenantId, String sessionId);

  /**
   * Moves a run to {@code to} following the state machine. Returns {@code true} when the run ends
   * in {@code to} — either it just transitioned, or it was already there (idempotent). Throws
   * {@link io.datastoria.server.agent.domain.IllegalRunTransitionException} when {@code to} is
   * unreachable from the current status (including a concurrent transition that landed in a
   * different terminal status) and {@link io.datastoria.server.api.error.NotFoundException} when
   * the run is missing or owned by another tenant.
   */
  boolean transition(String tenantId, String runId, AgentRunStatus to, RunTransition payload);

  /**
   * Server-internal cancellation entry point used by the run-lifecycle observer, which only has the
   * run id (from the {@code RunCancelled} event). The run's tenant is resolved from the row and the
   * underlying {@code UPDATE} still carries {@code tenant_id} in its {@code WHERE}; {@code runId}
   * is a globally-unique ULID. Returns {@code true} if the run is now cancelled.
   */
  boolean applyCancellation(String runId, Instant cancelledAt);
}
