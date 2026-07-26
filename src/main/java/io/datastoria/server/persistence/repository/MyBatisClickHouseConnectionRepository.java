package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.ClickHouseConnection;
import io.datastoria.server.persistence.entity.ClickHouseConnectionEntity;
import io.datastoria.server.persistence.mapper.ClickHouseConnectionMapper;
import io.datastoria.server.repository.ClickHouseConnectionRepository;

/** MyBatis-Plus adapter for {@code ds_clickhouse_connection}. */
@Repository
public class MyBatisClickHouseConnectionRepository implements ClickHouseConnectionRepository {

  private final ClickHouseConnectionMapper mapper;

  public MyBatisClickHouseConnectionRepository(ClickHouseConnectionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ClickHouseConnection save(ClickHouseConnection c) {
    Instant now = Instant.now();
    ClickHouseConnectionEntity e = ClickHouseConnectionEntity.fromDomain(c);
    e.setRevision(0L);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    mapper.insertConnection(e);
    return findById(c.id(), c.tenantId(), c.ownerUserId())
        .orElseThrow(() -> new NotFoundException("ClickHouseConnection", c.id()));
  }

  @Override
  public Optional<ClickHouseConnection> findById(String id, String tenantId, String ownerUserId) {
    return Optional.ofNullable(mapper.findById(id, tenantId, ownerUserId))
        .map(ClickHouseConnectionEntity::toDomain);
  }

  @Override
  public List<ClickHouseConnection> findAll(String tenantId, String ownerUserId) {
    return mapper.findAll(tenantId, ownerUserId).stream()
        .map(ClickHouseConnectionEntity::toDomain)
        .toList();
  }

  @Override
  public ClickHouseConnection update(ClickHouseConnection c, long expectedRevision) {
    ClickHouseConnectionEntity e = ClickHouseConnectionEntity.fromDomain(c);
    e.setUpdatedAt(Instant.now());
    int affected = mapper.updateCas(e, expectedRevision);
    if (affected == 0) {
      if (findById(c.id(), c.tenantId(), c.ownerUserId()).isEmpty()) {
        throw new NotFoundException("ClickHouseConnection", c.id());
      }
      throw new RevisionConflictException("ClickHouseConnection", c.id(), expectedRevision, -1);
    }
    return findById(c.id(), c.tenantId(), c.ownerUserId()).orElseThrow();
  }

  @Override
  public void softDelete(String id, String tenantId, String ownerUserId, long expectedRevision) {
    int affected = mapper.softDelete(id, tenantId, ownerUserId, expectedRevision, Instant.now());
    if (affected == 0) {
      throw new NotFoundException("ClickHouseConnection", id);
    }
  }
}
