package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import io.github.ccweixiao.datastoria.common.domain.AuditLog;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AuditLogEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.AuditLogMapper;
import io.github.ccweixiao.datastoria.dao.repository.AuditLogRepository;

/** MyBatis-Plus adapter for {@code ds_audit_log}. The DB-generated id is returned on the entity. */
@Repository
public class MyBatisAuditLogRepository implements AuditLogRepository {

  private final AuditLogMapper mapper;

  public MyBatisAuditLogRepository(AuditLogMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public AuditLog save(AuditLog entry) {
    Instant now = entry.createdAt() != null ? entry.createdAt() : Instant.now();
    AuditLogEntity e = new AuditLogEntity();
    e.setTenantId(entry.tenantId());
    e.setActor(entry.actor());
    e.setAction(entry.action());
    e.setResourceType(entry.resourceType());
    e.setResourceId(entry.resourceId());
    e.setRequestId(entry.requestId());
    e.setSafeDiff(entry.safeDiff());
    e.setResult(entry.result());
    e.setCreatedAt(now);
    mapper.insertAudit(e);
    return e.toDomain();
  }
}
