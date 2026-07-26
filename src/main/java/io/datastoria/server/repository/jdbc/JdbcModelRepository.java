package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.Model;
import io.datastoria.server.repository.ModelRepository;

@Repository
public class JdbcModelRepository implements ModelRepository {

  private static final RowMapper<Model> MAPPER =
      (rs, rowNum) ->
          new Model(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("provider_id"),
              rs.getString("model_key"),
              rs.getString("display_name"),
              rs.getString("description"),
              rs.getString("source"),
              rs.getBoolean("enabled"),
              rs.getBoolean("is_free"),
              rs.getString("capabilities_json"),
              rs.getString("generation_defaults_json"),
              rs.getString("secret_id"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private final JdbcClient jdbc;

  public JdbcModelRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Model save(Model m) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_model (id, tenant_id, provider_id, model_key, display_name,"
                + " description, source, enabled, is_free, capabilities_json,"
                + " generation_defaults_json, secret_id, revision, created_at, updated_at)"
                + " VALUES (:id, :tenantId, :providerId, :modelKey, :displayName,"
                + " :description, :source, :enabled, :isFree, :capabilitiesJson,"
                + " :generationDefaultsJson, :secretId, 0, :createdAt, :updatedAt)")
        .param("id", m.id())
        .param("tenantId", m.tenantId())
        .param("providerId", m.providerId())
        .param("modelKey", m.modelKey())
        .param("displayName", m.displayName())
        .param("description", m.description())
        .param("source", m.source())
        .param("enabled", m.enabled())
        .param("isFree", m.isFree())
        .param("capabilitiesJson", m.capabilitiesJson())
        .param("generationDefaultsJson", m.generationDefaultsJson())
        .param("secretId", m.secretId())
        .param("createdAt", SqlTimestamps.toParam(now))
        .param("updatedAt", SqlTimestamps.toParam(now))
        .update();
    return findById(m.id(), m.tenantId()).orElseThrow(() -> new NotFoundException("Model", m.id()));
  }

  @Override
  public Optional<Model> findById(String id, String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_model WHERE id = :id AND tenant_id = :tenantId"
                + " AND deleted_at IS NULL")
        .param("id", id)
        .param("tenantId", tenantId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<Model> findAll(String tenantId) {
    return jdbc.sql("SELECT * FROM ds_model WHERE tenant_id = :tenantId AND deleted_at IS NULL")
        .param("tenantId", tenantId)
        .query(MAPPER)
        .list();
  }

  @Override
  public List<Model> findEnabled(String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_model WHERE tenant_id = :tenantId AND enabled = TRUE"
                + " AND deleted_at IS NULL")
        .param("tenantId", tenantId)
        .query(MAPPER)
        .list();
  }

  @Override
  public boolean existsByProviderId(String providerId, String tenantId) {
    return Boolean.TRUE.equals(
        jdbc.sql(
                    "SELECT COUNT(*) FROM ds_model WHERE provider_id = :providerId"
                        + " AND tenant_id = :tenantId AND deleted_at IS NULL")
                .param("providerId", providerId)
                .param("tenantId", tenantId)
                .query(Integer.class)
                .single()
            > 0);
  }

  @Override
  public Model update(Model m, long expectedRevision) {
    Instant now = Instant.now();
    int affected =
        jdbc.sql(
                "UPDATE ds_model SET display_name = :displayName, description = :description,"
                    + " source = :source, enabled = :enabled, is_free = :isFree,"
                    + " capabilities_json = :capabilitiesJson,"
                    + " generation_defaults_json = :generationDefaultsJson,"
                    + " secret_id = :secretId, updated_at = :updatedAt,"
                    + " revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("displayName", m.displayName())
            .param("description", m.description())
            .param("source", m.source())
            .param("enabled", m.enabled())
            .param("isFree", m.isFree())
            .param("capabilitiesJson", m.capabilitiesJson())
            .param("generationDefaultsJson", m.generationDefaultsJson())
            .param("secretId", m.secretId())
            .param("updatedAt", SqlTimestamps.toParam(now))
            .param("id", m.id())
            .param("tenantId", m.tenantId())
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(m.id(), m.tenantId()).isEmpty()) {
        throw new NotFoundException("Model", m.id());
      }
      throw new RevisionConflictException("Model", m.id(), expectedRevision, -1);
    }
    return findById(m.id(), m.tenantId()).orElseThrow();
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int affected =
        jdbc.sql(
                "UPDATE ds_model SET deleted_at = :now"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("Model", id);
      }
      throw new RevisionConflictException("Model", id, expectedRevision, -1);
    }
  }
}
