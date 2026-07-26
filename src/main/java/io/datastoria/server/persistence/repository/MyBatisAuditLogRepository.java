package io.datastoria.server.persistence.repository;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.AuditLog;
import io.datastoria.server.persistence.entity.AuditLogEntity;
import io.datastoria.server.persistence.mapper.AuditLogMapper;
import io.datastoria.server.repository.AuditLogRepository;

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
