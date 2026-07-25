package io.datastoria.server.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.OAuthCredential;
import io.datastoria.server.repository.OAuthCredentialRepository;

@Repository
public class JdbcOAuthCredentialRepository implements OAuthCredentialRepository {

  private final JdbcClient jdbc;

  public JdbcOAuthCredentialRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<OAuthCredential> findByOwner(String tenantId, String userId, String providerKey) {
    return jdbc.sql(
            """
            SELECT * FROM ds_oauth_credential
            WHERE tenant_id = :tenant AND user_id = :user AND provider_key = :provider
            """)
        .param("tenant", tenantId)
        .param("user", userId)
        .param("provider", providerKey)
        .query(this::map)
        .optional();
  }

  @Override
  public OAuthCredential save(OAuthCredential value) {
    jdbc.sql(
            """
            INSERT INTO ds_oauth_credential
              (id,tenant_id,user_id,provider_key,secret_id,token_type,scope,expires_at,
               revision,created_at,updated_at)
            VALUES
              (:id,:tenant,:user,:provider,:secret,:tokenType,:scope,:expiresAt,
               :revision,:createdAt,:updatedAt)
            """)
        .params(params(value))
        .update();
    return value;
  }

  @Override
  public OAuthCredential update(OAuthCredential value, long expectedRevision) {
    int changed =
        jdbc.sql(
                """
                UPDATE ds_oauth_credential
                SET secret_id=:secret, token_type=:tokenType, scope=:scope, expires_at=:expiresAt,
                    revision=revision+1, updated_at=:updatedAt
                WHERE tenant_id=:tenant AND id=:id AND revision=:expectedRevision
                """)
            .params(params(value))
            .param("expectedRevision", expectedRevision)
            .update();
    if (changed != 1) {
      throw new org.springframework.dao.OptimisticLockingFailureException(
          "OAuth credential revision conflict");
    }
    return findByOwner(value.tenantId(), value.userId(), value.providerKey()).orElseThrow();
  }

  private java.util.Map<String, Object> params(OAuthCredential value) {
    java.util.Map<String, Object> params = new java.util.HashMap<>();
    params.put("id", value.id());
    params.put("tenant", value.tenantId());
    params.put("user", value.userId());
    params.put("provider", value.providerKey());
    params.put("secret", value.secretId());
    params.put("tokenType", value.tokenType());
    params.put("scope", value.scope());
    params.put(
        "expiresAt", value.expiresAt() == null ? null : SqlTimestamps.toParam(value.expiresAt()));
    params.put("revision", value.revision());
    params.put("createdAt", SqlTimestamps.toParam(value.createdAt()));
    params.put("updatedAt", SqlTimestamps.toParam(value.updatedAt()));
    return params;
  }

  private OAuthCredential map(ResultSet rs, int row) throws SQLException {
    return new OAuthCredential(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("user_id"),
        rs.getString("provider_key"),
        rs.getString("secret_id"),
        rs.getString("token_type"),
        rs.getString("scope"),
        SqlTimestamps.fromParam(rs, "expires_at"),
        rs.getLong("revision"),
        SqlTimestamps.fromParam(rs, "created_at"),
        SqlTimestamps.fromParam(rs, "updated_at"));
  }
}
