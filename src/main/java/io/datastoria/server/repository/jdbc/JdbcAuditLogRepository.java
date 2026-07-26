package io.datastoria.server.repository.jdbc;

import java.time.Instant;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.AuditLog;
import io.datastoria.server.repository.AuditLogRepository;

/**
 * JDBC implementation of {@link AuditLogRepository}. Append-only insert; no update/delete path.
 *
 * <p>The {@code id} is a database-generated auto-increment sequence number (never an external
 * resource identifier), so a {@link KeyHolder} is used to remain cross-dialect (SQLite uses {@code
 * last_insert_rowid()}, MySQL uses {@code LAST_INSERT_ID()}, and PostgreSQL uses its sequence).
 */
@Repository
public class JdbcAuditLogRepository implements AuditLogRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcAuditLogRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AuditLog save(AuditLog entry) {
    Instant now = entry.createdAt() != null ? entry.createdAt() : Instant.now();
    KeyHolder keyHolder = new GeneratedKeyHolder();
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("tenantId", entry.tenantId())
            .addValue("actor", entry.actor())
            .addValue("action", entry.action())
            .addValue("resourceType", entry.resourceType())
            .addValue("resourceId", entry.resourceId())
            .addValue("requestId", entry.requestId())
            .addValue("safeDiff", entry.safeDiff())
            .addValue("result", entry.result())
            .addValue("createdAt", SqlTimestamps.toParam(now));
    jdbc.update(
        "INSERT INTO ds_audit_log (tenant_id, actor, action, resource_type,"
            + " resource_id, request_id, safe_diff, result, created_at)"
            + " VALUES (:tenantId, :actor, :action, :resourceType, :resourceId,"
            + " :requestId, :safeDiff, :result, :createdAt)",
        params,
        keyHolder,
        new String[] {"id"});
    Number key = keyHolder.getKey();
    return new AuditLog(
        key == null ? null : key.longValue(),
        entry.tenantId(),
        entry.actor(),
        entry.action(),
        entry.resourceType(),
        entry.resourceId(),
        entry.requestId(),
        entry.safeDiff(),
        entry.result(),
        now);
  }
}
