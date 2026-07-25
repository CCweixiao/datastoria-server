package io.datastoria.server.agent.domain;

/** Resolution arrived after the durable action's server-side expiry time. */
public final class PendingActionExpiredException extends RuntimeException {

  public PendingActionExpiredException(String actionId) {
    super("Pending action has expired: " + actionId);
  }
}
