package io.github.ccweixiao.datastoria.common.agent;

import java.time.Instant;

/** Canonical resolution command used by the repository CAS. */
public record PendingActionResolution(
    PendingActionStatus status,
    String responseJson,
    String digest,
    String resolvedBy,
    Instant resolvedAt) {

  public PendingActionResolution {
    if (status == null
        || !status.isTerminal()
        || responseJson == null
        || digest == null
        || !digest.matches("[0-9a-f]{64}")
        || resolvedBy == null
        || resolvedAt == null) {
      throw new IllegalArgumentException("Invalid pending action resolution");
    }
  }
}
