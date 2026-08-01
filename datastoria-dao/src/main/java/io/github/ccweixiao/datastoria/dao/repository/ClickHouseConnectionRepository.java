package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;
import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.ClickHouseConnection;

public interface ClickHouseConnectionRepository {
  ClickHouseConnection save(ClickHouseConnection connection);

  Optional<ClickHouseConnection> findById(String id, String tenantId);

  List<ClickHouseConnection> findAll(String tenantId);

  ClickHouseConnection update(ClickHouseConnection connection, long expectedRevision);

  void softDelete(String id, String tenantId, long expectedRevision);
}
