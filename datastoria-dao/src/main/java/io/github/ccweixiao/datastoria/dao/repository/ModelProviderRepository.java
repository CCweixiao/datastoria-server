package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;
import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.ModelProvider;

/** CRUD for {@link ModelProvider} with tenant isolation and optimistic locking. */
public interface ModelProviderRepository {

  ModelProvider save(ModelProvider provider);

  Optional<ModelProvider> findById(String id, String tenantId);

  Optional<ModelProvider> findSystemById(String id, String tenantId);

  Optional<ModelProvider> findUserById(String id, String tenantId, String userId);

  List<ModelProvider> findAll(String tenantId);

  List<ModelProvider> findSystemProviders(String tenantId);

  List<ModelProvider> findUserProviders(String tenantId, String userId);

  List<ModelProvider> findAccessibleProviders(String tenantId, String userId);

  /** Updates non-sensitive fields; throws RevisionConflictException if revision mismatches. */
  ModelProvider update(ModelProvider provider, long expectedRevision);

  void softDelete(String id, String tenantId, long expectedRevision);
}
