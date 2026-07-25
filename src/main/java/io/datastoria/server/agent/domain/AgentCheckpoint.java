package io.datastoria.server.agent.domain;

import java.time.Instant;

/**
 * Opaque, DataStoria-adapter-serialized run state (docs/design/database-data-model.md §8). The
 * repository stores {@code stateJson} as an opaque string produced by DataStoria's own adapter
 * boundary; it NEVER exposes an AgentScope {@code State} type, and it MUST NOT contain a prompt,
 * API key, or provider credential (enforced by the adapter, not the DB).
 *
 * <p>Identity/upsert key is {@code (tenantId, runId, sequence)}: saving at an existing sequence
 * overwrites the state (and bumps {@code updatedAt}); saving at a new sequence appends. The latest
 * checkpoint for a run is the one with the greatest sequence.
 */
public record AgentCheckpoint(
    String id,
    String tenantId,
    String runId,
    long sequence,
    CheckpointType checkpointType,
    String stateJson,
    String codecVersion,
    String checksum,
    Instant createdAt,
    Instant updatedAt) {

  public AgentCheckpoint {
    if (sequence <= 0) {
      throw new IllegalArgumentException("checkpoint sequence must be > 0");
    }
  }
}
