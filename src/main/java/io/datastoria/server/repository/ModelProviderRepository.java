package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.ModelProvider;

/** CRUD for {@link ModelProvider} with tenant isolation and optimistic locking. */
public interface ModelProviderRepository {

  ModelProvider save(ModelProvider provider);

  Optional<ModelProvider> findById(String id, String tenantId);

  List<ModelProvider> findAll(String tenantId);

  /** Updates non-sensitive fields; throws RevisionConflictException if revision mismatches. */
  ModelProvider update(ModelProvider provider, long expectedRevision);

  void softDelete(String id, String tenantId, long expectedRevision);
}
