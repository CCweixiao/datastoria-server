package io.datastoria.server.agent.domain;

/**
 * Kind of {@link AgentCheckpoint}. P4 only produces {@link #RUN_STATE}; {@link #PENDING_ACTION}
 * (HITL pause/resume) is defined for P4.8. Persisted verbatim in {@code
 * ds_agent_checkpoint.checkpoint_type}.
 */
public enum CheckpointType {
  RUN_STATE,
  PENDING_ACTION;

  public String dbValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }

  public static CheckpointType fromDbValue(String value) {
    return CheckpointType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
  }
}
