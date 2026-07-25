package io.datastoria.server.agent.domain;

import java.util.Locale;

/** Kind of durable human-in-the-loop request associated with an Agent run. */
public enum PendingActionType {
  QUESTION,
  APPROVAL;

  public String dbValue() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static PendingActionType fromDbValue(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
