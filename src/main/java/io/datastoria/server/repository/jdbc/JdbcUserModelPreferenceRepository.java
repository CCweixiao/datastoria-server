package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.domain.UserModelPreference;
import io.datastoria.server.repository.UserModelPreferenceRepository;

@Repository
public class JdbcUserModelPreferenceRepository implements UserModelPreferenceRepository {

  private static final RowMapper<UserModelPreference> MAPPER =
      (rs, rowNum) ->
          new UserModelPreference(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("user_id"),
              rs.getString("selected_model_id"),
              rs.getString("preference_json"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcUserModelPreferenceRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UserModelPreference upsert(
      String tenantId, String userId, String selectedModelId, String preferenceJson, Long ifMatch) {
    Optional<UserModelPreference> existing = findByUser(tenantId, userId);
    Instant now = Instant.now();
    if (existing.isPresent()) {
      UserModelPreference current = existing.get();
      long expected = ifMatch != null ? ifMatch : current.revision();
      int rows =
          jdbc.sql(
                  "UPDATE ds_user_model_preference SET selected_model_id = :modelId,"
                      + " preference_json = :prefJson, revision = revision + 1, updated_at = :now"
                      + " WHERE tenant_id = :tenantId AND user_id = :userId"
                      + " AND revision = :expected")
              .param("modelId", selectedModelId)
              .param("prefJson", preferenceJson)
              .param("now", SqlTimestamps.toParam(now))
              .param("tenantId", tenantId)
              .param("userId", userId)
              .param("expected", expected)
              .update();
      if (rows == 0) {
        throw new RevisionConflictException(
            "UserModelPreference", current.id(), expected, current.revision());
      }
      return findByUser(tenantId, userId).orElseThrow();
    }
    String id = Ulid.next();
    jdbc.sql(
            "INSERT INTO ds_user_model_preference"
                + " (id, tenant_id, user_id, selected_model_id, preference_json, revision,"
                + " created_at, updated_at)"
                + " VALUES (:id, :tenantId, :userId, :modelId, :prefJson, 0, :now, :now)")
        .param("id", id)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("modelId", selectedModelId)
        .param("prefJson", preferenceJson)
        .param("now", SqlTimestamps.toParam(now))
        .update();
    return findByUser(tenantId, userId).orElseThrow();
  }

  @Override
  public Optional<UserModelPreference> findByUser(String tenantId, String userId) {
    return jdbc.sql(
            "SELECT * FROM ds_user_model_preference"
                + " WHERE tenant_id = :tenantId AND user_id = :userId")
        .param("tenantId", tenantId)
        .param("userId", userId)
        .query(MAPPER)
        .optional();
  }
}
