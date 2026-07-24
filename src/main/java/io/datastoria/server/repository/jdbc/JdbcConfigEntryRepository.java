package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.ConfigEntry;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.repository.ConfigEntryRepository;

@Repository
public class JdbcConfigEntryRepository implements ConfigEntryRepository {

  private static final RowMapper<ConfigEntry> MAPPER =
      (rs, rowNum) ->
          new ConfigEntry(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("scope_type"),
              rs.getString("scope_id"),
              rs.getString("config_key"),
              rs.getString("value_json"),
              rs.getString("schema_version"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private final JdbcClient jdbc;

  public JdbcConfigEntryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ConfigEntry save(ConfigEntry entry) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_config_entry (id, tenant_id, scope_type, scope_id, config_key,"
                + " value_json, schema_version, revision, created_at, updated_at)"
                + " VALUES (:id, :tenantId, :scopeType, :scopeId, :configKey, :valueJson,"
                + " :schemaVersion, :revision, :createdAt, :updatedAt)")
        .param("id", entry.id() != null ? entry.id() : Ulid.next())
        .param("tenantId", entry.tenantId())
        .param("scopeType", entry.scopeType())
        .param("scopeId", entry.scopeId())
        .param("configKey", entry.configKey())
        .param("valueJson", entry.valueJson())
        .param("schemaVersion", entry.schemaVersion() != null ? entry.schemaVersion() : "1")
        .param("revision", entry.revision())
        .param(
            "createdAt", SqlTimestamps.toParam(entry.createdAt() != null ? entry.createdAt() : now))
        .param(
            "updatedAt", SqlTimestamps.toParam(entry.updatedAt() != null ? entry.updatedAt() : now))
        .update();
    return findById(entry.id(), entry.tenantId()).orElseThrow();
  }

  @Override
  public ConfigEntry upsertUserEntry(
      String tenantId, String userId, String configKey, String valueJson, Long ifMatch) {
    Optional<ConfigEntry> existing =
        jdbc.sql(
                "SELECT * FROM ds_config_entry"
                    + " WHERE tenant_id = :tenantId AND scope_type = 'user'"
                    + " AND scope_id = :userId AND config_key = :configKey"
                    + " AND deleted_at IS NULL")
            .param("tenantId", tenantId)
            .param("userId", userId)
            .param("configKey", configKey)
            .query(MAPPER)
            .optional();
    Instant now = Instant.now();
    if (existing.isPresent()) {
      ConfigEntry current = existing.get();
      long expected = ifMatch != null ? ifMatch : current.revision();
      int rows =
          jdbc.sql(
                  "UPDATE ds_config_entry SET value_json = :valueJson,"
                      + " revision = revision + 1, updated_at = :now"
                      + " WHERE id = :id AND tenant_id = :tenantId"
                      + " AND revision = :expected AND deleted_at IS NULL")
              .param("valueJson", valueJson)
              .param("now", SqlTimestamps.toParam(now))
              .param("id", current.id())
              .param("tenantId", tenantId)
              .param("expected", expected)
              .update();
      if (rows == 0) {
        throw new RevisionConflictException(
            "ConfigEntry", current.id(), expected, current.revision());
      }
      return findById(current.id(), tenantId).orElseThrow();
    }
    ConfigEntry entry =
        new ConfigEntry(
            Ulid.next(), tenantId, "user", userId, configKey, valueJson, "1", 0, now, now, null);
    return save(entry);
  }

  @Override
  public Optional<ConfigEntry> findById(String id, String tenantId) {
    return jdbc.sql("SELECT * FROM ds_config_entry WHERE id = :id AND tenant_id = :tenantId")
        .param("id", id)
        .param("tenantId", tenantId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<ConfigEntry> findEffective(String tenantId, String userId) {
    return jdbc.sql(
            "SELECT * FROM ds_config_entry"
                + " WHERE tenant_id = :tenantId AND deleted_at IS NULL"
                + " AND ("
                + "   scope_type = 'system'"
                + "   OR (scope_type = 'tenant' AND scope_id = :tenantId)"
                + "   OR (scope_type = 'user' AND scope_id = :userId)"
                + " ) ORDER BY revision")
        .param("tenantId", tenantId)
        .param("userId", userId)
        .query(MAPPER)
        .list();
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int rows =
        jdbc.sql(
                "UPDATE ds_config_entry SET deleted_at = :now, revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expected AND deleted_at IS NULL")
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("expected", expectedRevision)
            .update();
    if (rows == 0) {
      throw new NotFoundException("ConfigEntry", id);
    }
  }
}
