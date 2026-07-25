package io.datastoria.server.agent.application;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import io.datastoria.server.agent.domain.AgentCheckpoint;
import io.datastoria.server.agent.domain.CheckpointContent;
import io.datastoria.server.agent.domain.CheckpointType;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.repository.AgentCheckpointRepository;

/**
 * Wires {@link CheckpointContent} (produced by the runtime {@code CheckpointStateAdapter}) into
 * {@code ds_agent_checkpoint} and back. AgentScope-free: it only knows the domain {@code content}
 * triple and the repository; the AgentScope {@code State} ↔ {@code CheckpointContent} conversion
 * stays in the runtime adapter.
 *
 * <p>Tenant isolation and the P4.3 atomic upsert are inherited from {@link
 * AgentCheckpointRepository}: every read filters by {@code tenant_id}, and {@code save} overwrites
 * at {@code (tenant, run, sequence)} or appends at a new sequence.
 */
@Component
public final class CheckpointStore {

  private final AgentCheckpointRepository repository;

  public CheckpointStore(AgentCheckpointRepository repository) {
    this.repository = repository;
  }

  /** Persists a checkpoint content row at {@code (tenantId, runId, sequence)}. */
  public void save(
      String tenantId,
      String runId,
      long sequence,
      CheckpointType type,
      CheckpointContent content) {
    Instant now = Instant.now();
    AgentCheckpoint row =
        new AgentCheckpoint(
            Ulid.next(),
            tenantId,
            runId,
            sequence,
            type,
            content.stateJson(),
            content.codecVersion(),
            content.checksum(),
            now,
            now);
    repository.save(row);
  }

  /**
   * Loads the latest checkpoint content for a run under {@code tenantId}, or empty. Rows lacking a
   * checksum (the column is nullable for non-adapter producers) are skipped rather than wrapped
   * into an invalid {@link CheckpointContent}.
   */
  public Optional<CheckpointContent> loadLatest(String tenantId, String runId) {
    return repository
        .findLatest(tenantId, runId)
        .filter(row -> row.checksum() != null && !row.checksum().isBlank())
        .map(row -> new CheckpointContent(row.codecVersion(), row.stateJson(), row.checksum()));
  }
}
