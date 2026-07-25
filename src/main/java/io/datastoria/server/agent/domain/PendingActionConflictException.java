package io.datastoria.server.agent.domain;

/** A resolved action was retried with a different decision or payload. */
public final class PendingActionConflictException extends RuntimeException {

  public PendingActionConflictException(String actionId) {
    super("Pending action was already resolved differently: " + actionId);
  }
}
