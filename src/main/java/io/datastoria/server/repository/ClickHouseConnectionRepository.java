package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.ClickHouseConnection;

public interface ClickHouseConnectionRepository {
  ClickHouseConnection save(ClickHouseConnection connection);

  Optional<ClickHouseConnection> findById(String id, String tenantId, String ownerUserId);

  List<ClickHouseConnection> findAll(String tenantId, String ownerUserId);

  ClickHouseConnection update(ClickHouseConnection connection, long expectedRevision);

  void softDelete(String id, String tenantId, String ownerUserId, long expectedRevision);
}
