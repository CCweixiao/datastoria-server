package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.SessionShare;
import io.datastoria.server.repository.SessionShareRepository;

@Repository
public class JdbcSessionShareRepository implements SessionShareRepository {

  private static final RowMapper<SessionShare> MAPPER =
      (rs, rowNum) ->
          new SessionShare(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("session_id"),
              rs.getString("owner_user_id"),
              rs.getString("token_hash"),
              SqlTimestamps.fromParam(rs, "expires_at"),
              SqlTimestamps.fromParam(rs, "revoked_at"),
              SqlTimestamps.fromParam(rs, "created_at"));

  private final JdbcClient jdbc;

  public JdbcSessionShareRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public SessionShare issue(SessionShare s) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_session_share"
                + " (id, tenant_id, session_id, owner_user_id, token_hash, expires_at,"
                + " revoked_at, created_at)"
                + " VALUES (:id, :tenantId, :sessionId, :ownerUserId, :tokenHash,"
                + " :expiresAt, NULL, :now)")
        .param("id", s.id())
        .param("tenantId", s.tenantId())
        .param("sessionId", s.sessionId())
        .param("ownerUserId", s.ownerUserId())
        .param("tokenHash", s.tokenHash())
        .param("expiresAt", SqlTimestamps.toParamMillis(s.expiresAt()))
        .param("now", SqlTimestamps.toParamMillis(now))
        .update();
    return findByTokenHash(s.tokenHash()).orElseThrow();
  }

  @Override
  public Optional<SessionShare> findActive(String sessionId, String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_session_share"
                + " WHERE tenant_id = :tenantId AND session_id = :sessionId"
                + " AND revoked_at IS NULL ORDER BY created_at DESC LIMIT 1")
        .param("tenantId", tenantId)
        .param("sessionId", sessionId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public Optional<SessionShare> findByTokenHash(String tokenHash) {
    return jdbc.sql("SELECT * FROM ds_session_share WHERE token_hash = :tokenHash LIMIT 1")
        .param("tokenHash", tokenHash)
        .query(MAPPER)
        .optional();
  }

  @Override
  public int revoke(String sessionId, String tenantId) {
    return jdbc.sql(
            "UPDATE ds_session_share SET revoked_at = :now"
                + " WHERE tenant_id = :tenantId AND session_id = :sessionId"
                + " AND revoked_at IS NULL")
        .param("now", SqlTimestamps.toParamMillis(Instant.now()))
        .param("tenantId", tenantId)
        .param("sessionId", sessionId)
        .update();
  }
}
