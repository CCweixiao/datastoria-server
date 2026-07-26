package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.repository.ModelProviderRepository;

@Repository
public class JdbcModelProviderRepository implements ModelProviderRepository {

  private static final RowMapper<ModelProvider> MAPPER =
      (rs, rowNum) ->
          new ModelProvider(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("provider_key"),
              rs.getString("display_name"),
              rs.getString("base_url"),
              rs.getString("auth_type"),
              rs.getBoolean("enabled"),
              rs.getString("config_json"),
              rs.getString("secret_id"),
              rs.getLong("revision"),
              rs.getString("created_by"),
              rs.getString("updated_by"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private final JdbcClient jdbc;

  public JdbcModelProviderRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ModelProvider save(ModelProvider p) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_model_provider (id, tenant_id, provider_key, display_name,"
                + " base_url, auth_type, enabled, config_json, secret_id, revision,"
                + " created_by, updated_by, created_at, updated_at)"
                + " VALUES (:id, :tenantId, :providerKey, :displayName, :baseUrl,"
                + " :authType, :enabled, :configJson, :secretId, 0, :createdBy,"
                + " :updatedBy, :createdAt, :updatedAt)")
        .param("id", p.id())
        .param("tenantId", p.tenantId())
        .param("providerKey", p.providerKey())
        .param("displayName", p.displayName())
        .param("baseUrl", p.baseUrl())
        .param("authType", p.authType())
        .param("enabled", p.enabled())
        .param("configJson", p.configJson())
        .param("secretId", p.secretId())
        .param("createdBy", p.createdBy())
        .param("updatedBy", p.updatedBy())
        .param("createdAt", SqlTimestamps.toParam(now))
        .param("updatedAt", SqlTimestamps.toParam(now))
        .update();
    return findById(p.id(), p.tenantId())
        .orElseThrow(() -> new NotFoundException("ModelProvider", p.id()));
  }

  @Override
  public Optional<ModelProvider> findById(String id, String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_model_provider WHERE id = :id AND tenant_id = :tenantId"
                + " AND deleted_at IS NULL")
        .param("id", id)
        .param("tenantId", tenantId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<ModelProvider> findAll(String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_model_provider WHERE tenant_id = :tenantId AND deleted_at IS NULL")
        .param("tenantId", tenantId)
        .query(MAPPER)
        .list();
  }

  @Override
  public ModelProvider update(ModelProvider p, long expectedRevision) {
    Instant now = Instant.now();
    int affected =
        jdbc.sql(
                "UPDATE ds_model_provider SET display_name = :displayName, base_url = :baseUrl,"
                    + " auth_type = :authType, enabled = :enabled, config_json = :configJson,"
                    + " updated_by = :updatedBy, updated_at = :updatedAt,"
                    + " revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("displayName", p.displayName())
            .param("baseUrl", p.baseUrl())
            .param("authType", p.authType())
            .param("enabled", p.enabled())
            .param("configJson", p.configJson())
            .param("updatedBy", p.updatedBy())
            .param("updatedAt", SqlTimestamps.toParam(now))
            .param("id", p.id())
            .param("tenantId", p.tenantId())
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(p.id(), p.tenantId()).isEmpty()) {
        throw new NotFoundException("ModelProvider", p.id());
      }
      throw new RevisionConflictException("ModelProvider", p.id(), expectedRevision, -1);
    }
    return findById(p.id(), p.tenantId()).orElseThrow();
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int affected =
        jdbc.sql(
                "UPDATE ds_model_provider SET deleted_at = :now"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("ModelProvider", id);
      }
      throw new RevisionConflictException("ModelProvider", id, expectedRevision, -1);
    }
  }

  /** Links (or unlinks) a secret to this provider. Used by credential rotation. */
  public void updateSecretId(String id, String tenantId, String secretId) {
    int affected =
        jdbc.sql(
                "UPDATE ds_model_provider SET secret_id = :secretId, updated_at = :now"
                    + " WHERE id = :id AND tenant_id = :tenantId AND deleted_at IS NULL")
            .param("secretId", secretId)
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .update();
    if (affected == 0) {
      throw new NotFoundException("ModelProvider", id);
    }
  }
}
