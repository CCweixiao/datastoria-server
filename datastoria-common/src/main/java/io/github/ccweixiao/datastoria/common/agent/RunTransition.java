package io.github.ccweixiao.datastoria.common.agent;

import java.time.Instant;

/**
 * Payload for an {@link AgentRunRepository#transition} call. Only the fields relevant to the target
 * status need to be set; the repository uses {@code COALESCE} so unset (null) fields preserve the
 * existing column value rather than clobbering it.
 *
 * <p>{@code errorCode}/{@code safeMessage} come from a {@link RunFailureCode} (fixed, leak-free
 * strings). {@code usageJson} is non-sensitive token accounting. None of these fields may carry a
 * prompt, API key, or provider credential.
 */
public record RunTransition(
    Instant startedAt, Instant finishedAt, String errorCode, String safeMessage, String usageJson) {

  public static RunTransition starting(Instant startedAt) {
    return new RunTransition(startedAt, null, null, null, null);
  }

  public static RunTransition completing(Instant finishedAt, String usageJson) {
    return new RunTransition(null, finishedAt, null, null, usageJson);
  }

  public static RunTransition failing(Instant finishedAt, RunFailureCode failure) {
    return new RunTransition(null, finishedAt, failure.name(), failure.safeMessage(), null);
  }

  public static RunTransition cancelling(Instant finishedAt) {
    return new RunTransition(null, finishedAt, null, null, null);
  }

  public static RunTransition waitingForInput() {
    return new RunTransition(null, null, null, null, null);
  }
}
