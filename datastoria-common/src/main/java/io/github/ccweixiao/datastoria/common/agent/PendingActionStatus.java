package io.github.ccweixiao.datastoria.common.agent;

import java.util.Locale;

/** Revision-guarded lifecycle for a durable HITL action. */
public enum PendingActionStatus {
  PENDING,
  RESPONDED,
  APPROVED,
  DENIED,
  EXPIRED,
  CANCELLED;

  public String dbValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static PendingActionStatus fromDbValue(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }

  public boolean isTerminal() {
    return this != PENDING;
  }

  public boolean isValidResolutionFor(PendingActionType type) {
    return switch (type) {
      case QUESTION -> this == RESPONDED;
      case APPROVAL -> this == APPROVED || this == DENIED;
    };
  }
}
