package io.datastoria.server.repository;

import io.datastoria.server.domain.AuditLog;

/** Append-only audit log repository. Records are inserted only; never updated or deleted. */
public interface AuditLogRepository {

  AuditLog save(AuditLog entry);
}
