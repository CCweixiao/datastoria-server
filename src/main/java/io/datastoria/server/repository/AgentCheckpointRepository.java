package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.agent.domain.AgentCheckpoint;

/**
 * Persistent access to {@code ds_agent_checkpoint}. Every method is scoped by {@code tenantId}.
 *
 * <p><b>Upsert semantics:</b> {@link #save} inserts at {@code (tenantId, runId, sequence)} or, if a
 * row already exists at that key, overwrites {@code stateJson}/{@code codecVersion}/{@code
 * checksum} and bumps {@code updatedAt} (created_at is preserved). The latest checkpoint for a run
 * is the row with the greatest sequence.
 */
public interface AgentCheckpointRepository {

  /** Upserts a checkpoint at {@code (tenantId, runId, sequence)} (overwrite-if-exists). */
  void save(AgentCheckpoint checkpoint);

  /** Latest checkpoint for the run (max sequence), or empty. */
  Optional<AgentCheckpoint> findLatest(String tenantId, String runId);

  /** The checkpoint at an exact sequence, or empty. */
  Optional<AgentCheckpoint> findBySequence(String tenantId, String runId, long sequence);

  /** All checkpoints for the run, ascending by sequence. */
  List<AgentCheckpoint> findAllByRun(String tenantId, String runId);
}
