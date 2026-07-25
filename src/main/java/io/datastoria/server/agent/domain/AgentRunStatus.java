package io.datastoria.server.agent.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative status set for {@link AgentRun}, persisted verbatim (lowercase) in the {@code
 * ds_agent_run.status} column (docs/design/database-data-model.md §8). The enum also encodes the
 * run state machine: which transitions are allowed, which states are terminal, and the idempotency
 * rule that re-asserting a terminal status is a no-op success.
 *
 * <p>P4 exercises {@code QUEUED → RUNNING} and {@code RUNNING → SUCCEEDED/FAILED/CANCELLED}. {@code
 * WAITING_INPUT} (HITL) and {@code EXPIRED} (timeout) are defined for P4.6/P4.8 but follow the same
 * rules.
 */
public enum AgentRunStatus {
  QUEUED,
  RUNNING,
  WAITING_INPUT,
  SUCCEEDED,
  FAILED,
  CANCELLED,
  EXPIRED;

  /** Persisted column value (lowercase, matches the V5 CHECK constraint). */
  public String dbValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  /** Terminal statuses never return to a non-terminal state. */
  public boolean isTerminal() {
    return this == SUCCEEDED || this == FAILED || this == CANCELLED || this == EXPIRED;
  }

  /**
   * State-machine predicate.
   *
   * <p>Self-transitions are always allowed and treated as idempotent no-ops by the repository
   * (complete/fail/cancel called twice succeeds without changing the row). A terminal status can
   * never move to a different status — in particular never back to {@code RUNNING} — so concurrent
   * terminal transitions cannot overwrite each other.
   */
  public boolean canTransitionTo(AgentRunStatus target) {
    if (this == target) {
      return true;
    }
    if (this.isTerminal()) {
      return false;
    }
    return ALLOWED.getOrDefault(this, Set.of()).contains(target);
  }

  /** Parses the stored column value; throws on unknown values so a corrupt row fails loudly. */
  public static AgentRunStatus fromDbValue(String value) {
    if (value == null) {
      throw new IllegalArgumentException("AgentRunStatus db value is null");
    }
    return AgentRunStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT));
  }

  private static final Map<AgentRunStatus, Set<AgentRunStatus>> ALLOWED =
      Map.of(
          QUEUED,
          Set.of(RUNNING, FAILED, CANCELLED, EXPIRED),
          RUNNING,
          Set.of(WAITING_INPUT, SUCCEEDED, FAILED, CANCELLED, EXPIRED),
          WAITING_INPUT,
          EnumSet.of(RUNNING, SUCCEEDED, FAILED, CANCELLED, EXPIRED));
}
