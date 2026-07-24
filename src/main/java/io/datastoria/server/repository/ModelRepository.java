package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.Model;

/** CRUD for {@link Model} with tenant isolation and optimistic locking. */
public interface ModelRepository {

  Model save(Model model);

  Optional<Model> findById(String id, String tenantId);

  List<Model> findAll(String tenantId);

  List<Model> findEnabled(String tenantId);

  boolean existsByProviderId(String providerId, String tenantId);

  Model update(Model model, long expectedRevision);

  void softDelete(String id, String tenantId, long expectedRevision);
}
