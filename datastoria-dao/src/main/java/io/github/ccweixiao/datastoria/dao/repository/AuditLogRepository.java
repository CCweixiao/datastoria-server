package io.github.ccweixiao.datastoria.dao.repository;

import io.github.ccweixiao.datastoria.common.domain.AuditLog;

/** Append-only audit log repository. Records are inserted only; never updated or deleted. */
public interface AuditLogRepository {

  AuditLog save(AuditLog entry);
}
