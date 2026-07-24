package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.AgentDefinition;

/** CRUD for {@link AgentDefinition} with optimistic locking and atomic publish. */
public interface AgentDefinitionRepository {

  AgentDefinition save(AgentDefinition def);

  Optional<AgentDefinition> findById(String id, String tenantId);

  List<AgentDefinition> findAll(String tenantId);

  AgentDefinition update(AgentDefinition def, long expectedRevision);

  /** Atomically sets {@code published_revision_id} and bumps the definition revision. */
  void publish(String id, String tenantId, String revisionId, long expectedRevision);

  void disable(String id, String tenantId, long expectedRevision);

  void softDelete(String id, String tenantId, long expectedRevision);
}
