package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.ClickHouseConnection;
import io.datastoria.server.repository.ClickHouseConnectionRepository;

@Repository
public class JdbcClickHouseConnectionRepository implements ClickHouseConnectionRepository {

  private static final RowMapper<ClickHouseConnection> MAPPER =
      (rs, rowNum) ->
          new ClickHouseConnection(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("owner_user_id"),
              rs.getString("name"),
              rs.getString("url"),
              rs.getString("username"),
              rs.getString("cluster_name"),
              rs.getBytes("password_cipher"),
              rs.getBytes("password_nonce"),
              rs.getString("password_key_version"),
              rs.getString("password_masked_hint"),
              rs.getBoolean("enabled"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private final JdbcClient jdbc;

  public JdbcClickHouseConnectionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ClickHouseConnection save(ClickHouseConnection c) {
    Instant now = Instant.now();
    jdbc.sql(
            """
            INSERT INTO ds_clickhouse_connection
              (id, tenant_id, owner_user_id, name, url, username, cluster_name,
               password_cipher, password_nonce, password_key_version, password_masked_hint,
               enabled, revision, created_at, updated_at)
            VALUES
              (:id, :tenantId, :ownerUserId, :name, :url, :username, :cluster,
               :passwordCipher, :passwordNonce, :passwordKeyVersion, :passwordMaskedHint,
               :enabled, 0, :createdAt, :updatedAt)
            """)
        .param("id", c.id())
        .param("tenantId", c.tenantId())
        .param("ownerUserId", c.ownerUserId())
        .param("name", c.name())
        .param("url", c.url())
        .param("username", c.username())
        .param("cluster", c.cluster())
        .param("passwordCipher", c.passwordCipher())
        .param("passwordNonce", c.passwordNonce())
        .param("passwordKeyVersion", c.passwordKeyVersion())
        .param("passwordMaskedHint", c.passwordMaskedHint())
        .param("enabled", c.enabled())
        .param("createdAt", SqlTimestamps.toParam(now))
        .param("updatedAt", SqlTimestamps.toParam(now))
        .update();
    return findById(c.id(), c.tenantId(), c.ownerUserId()).orElseThrow();
  }

  @Override
  public Optional<ClickHouseConnection> findById(String id, String tenantId, String ownerUserId) {
    return jdbc.sql(
            """
            SELECT * FROM ds_clickhouse_connection
            WHERE id = :id AND tenant_id = :tenantId AND owner_user_id = :ownerUserId
              AND deleted_at IS NULL
            """)
        .param("id", id)
        .param("tenantId", tenantId)
        .param("ownerUserId", ownerUserId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<ClickHouseConnection> findAll(String tenantId, String ownerUserId) {
    return jdbc.sql(
            """
            SELECT * FROM ds_clickhouse_connection
            WHERE tenant_id = :tenantId AND owner_user_id = :ownerUserId AND deleted_at IS NULL
            ORDER BY created_at
            """)
        .param("tenantId", tenantId)
        .param("ownerUserId", ownerUserId)
        .query(MAPPER)
        .list();
  }

  @Override
  public ClickHouseConnection update(ClickHouseConnection c, long expectedRevision) {
    int affected =
        jdbc.sql(
                """
                UPDATE ds_clickhouse_connection
                SET name = :name, url = :url, username = :username, cluster_name = :cluster,
                    password_cipher = :passwordCipher, password_nonce = :passwordNonce,
                    password_key_version = :passwordKeyVersion,
                    password_masked_hint = :passwordMaskedHint, enabled = :enabled,
                    revision = revision + 1, updated_at = :updatedAt
                WHERE id = :id AND tenant_id = :tenantId AND owner_user_id = :ownerUserId
                  AND revision = :expectedRevision AND deleted_at IS NULL
                """)
            .param("name", c.name())
            .param("url", c.url())
            .param("username", c.username())
            .param("cluster", c.cluster())
            .param("passwordCipher", c.passwordCipher())
            .param("passwordNonce", c.passwordNonce())
            .param("passwordKeyVersion", c.passwordKeyVersion())
            .param("passwordMaskedHint", c.passwordMaskedHint())
            .param("enabled", c.enabled())
            .param("updatedAt", SqlTimestamps.toParam(Instant.now()))
            .param("id", c.id())
            .param("tenantId", c.tenantId())
            .param("ownerUserId", c.ownerUserId())
            .param("expectedRevision", expectedRevision)
            .update();
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
    int affected =
        jdbc.sql(
                """
                UPDATE ds_clickhouse_connection
                SET deleted_at = :deletedAt
                WHERE id = :id AND tenant_id = :tenantId AND owner_user_id = :ownerUserId
                  AND revision = :expectedRevision AND deleted_at IS NULL
                """)
            .param("deletedAt", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("ownerUserId", ownerUserId)
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      throw new NotFoundException("ClickHouseConnection", id);
    }
  }
}
