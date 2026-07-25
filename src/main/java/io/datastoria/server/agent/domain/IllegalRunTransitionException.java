package io.datastoria.server.agent.domain;

/**
 * Raised when a run status transition is not allowed by {@link AgentRunStatus#canTransitionTo}, or
 * when a conditional (optimistic-lock) update could not land because a concurrent transition moved
 * the run to a different status. In both cases the existing row is left untouched — terminal states
 * can never be overwritten by a competing transition.
 *
 * <p>The message contains only the run id and statuses (no prompt, credential, or provider text).
 */
public class IllegalRunTransitionException extends RuntimeException {

  private final String runId;
  private final AgentRunStatus from;
  private final AgentRunStatus to;

  public IllegalRunTransitionException(String runId, AgentRunStatus from, AgentRunStatus to) {
    super("Illegal run transition for " + runId + ": " + from + " -> " + to);
    this.runId = runId;
    this.from = from;
    this.to = to;
  }

  public String runId() {
    return runId;
  }

  public AgentRunStatus from() {
    return from;
  }

  public AgentRunStatus to() {
    return to;
  }
}
