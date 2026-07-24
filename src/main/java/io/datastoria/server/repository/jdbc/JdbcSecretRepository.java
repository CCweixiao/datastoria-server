package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.Secret;
import io.datastoria.server.repository.SecretRepository;

@Repository
public class JdbcSecretRepository implements SecretRepository {

  private static final RowMapper<Secret> FULL_MAPPER =
      (rs, rowNum) ->
          new Secret(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("owner_user_id"),
              rs.getString("secret_kind"),
              rs.getBytes("cipher_text"),
              rs.getString("key_version"),
              rs.getBytes("nonce"),
              rs.getString("masked_hint"),
              SqlTimestamps.fromParam(rs, "expires_at"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  /** Masked mapper — selects only display fields; cipher_text and nonce are never read. */
  private static final RowMapper<Secret> MASKED_MAPPER =
      (rs, rowNum) ->
          new Secret(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("owner_user_id"),
              rs.getString("secret_kind"),
              null,
              rs.getString("key_version"),
              null,
              rs.getString("masked_hint"),
              SqlTimestamps.fromParam(rs, "expires_at"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private final JdbcClient jdbc;

  public JdbcSecretRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Secret save(Secret s) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_secret (id, tenant_id, owner_user_id, secret_kind,"
                + " cipher_text, key_version, nonce, masked_hint, expires_at,"
                + " created_at, updated_at)"
                + " VALUES (:id, :tenantId, :ownerUserId, :secretKind, :cipherText,"
                + " :keyVersion, :nonce, :maskedHint, :expiresAt, :createdAt, :updatedAt)")
        .param("id", s.id())
        .param("tenantId", s.tenantId())
        .param("ownerUserId", s.ownerUserId())
        .param("secretKind", s.secretKind())
        .param("cipherText", s.cipherText())
        .param("keyVersion", s.keyVersion())
        .param("nonce", s.nonce())
        .param("maskedHint", s.maskedHint())
        .param("expiresAt", s.expiresAt() == null ? null : SqlTimestamps.toParam(s.expiresAt()))
        .param("createdAt", SqlTimestamps.toParam(now))
        .param("updatedAt", SqlTimestamps.toParam(now))
        .update();
    return findMaskedById(s.id(), s.tenantId()).orElseThrow();
  }

  @Override
  public Optional<Secret> findEncryptedById(String id, String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_secret WHERE id = :id AND tenant_id = :tenantId"
                + " AND deleted_at IS NULL")
        .param("id", id)
        .param("tenantId", tenantId)
        .query(FULL_MAPPER)
        .optional();
  }

  @Override
  public Optional<Secret> findMaskedById(String id, String tenantId) {
    return jdbc.sql(
            "SELECT id, tenant_id, owner_user_id, secret_kind, key_version,"
                + " masked_hint, expires_at, created_at, updated_at, deleted_at"
                + " FROM ds_secret WHERE id = :id AND tenant_id = :tenantId"
                + " AND deleted_at IS NULL")
        .param("id", id)
        .param("tenantId", tenantId)
        .query(MASKED_MAPPER)
        .optional();
  }

  @Override
  public void softDelete(String id, String tenantId) {
    jdbc.sql("UPDATE ds_secret SET deleted_at = :now WHERE id = :id AND tenant_id = :tenantId")
        .param("now", SqlTimestamps.toParam(Instant.now()))
        .param("id", id)
        .param("tenantId", tenantId)
        .update();
  }
}
