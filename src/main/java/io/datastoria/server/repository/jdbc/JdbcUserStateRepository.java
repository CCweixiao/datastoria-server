package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.UserState;
import io.datastoria.server.repository.UserStateRepository;

@Repository
public class JdbcUserStateRepository implements UserStateRepository {

  private static final RowMapper<UserState> MAPPER =
      (rs, rowNum) ->
          new UserState(
              rs.getString("tenant_id"),
              rs.getString("user_id"),
              rs.getString("namespace"),
              rs.getString("state_key"),
              rs.getString("value_json"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcUserStateRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<UserState> findAll(String tenantId, String userId, String namespace) {
    return jdbc.sql(
            """
            SELECT * FROM ds_user_state
            WHERE tenant_id = :tenantId AND user_id = :userId AND namespace = :namespace
            ORDER BY state_key
            """)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("namespace", namespace)
        .query(MAPPER)
        .list();
  }

  @Override
  public Optional<UserState> find(String tenantId, String userId, String namespace, String key) {
    return jdbc.sql(
            """
            SELECT * FROM ds_user_state
            WHERE tenant_id = :tenantId AND user_id = :userId
              AND namespace = :namespace AND state_key = :key
            """)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("namespace", namespace)
        .param("key", key)
        .query(MAPPER)
        .optional();
  }

  @Override
  public UserState upsert(UserState state, Long expectedRevision) {
    if (expectedRevision == null) {
      return upsertLastWriteWins(state);
    }

    Optional<UserState> existing =
        find(state.tenantId(), state.userId(), state.namespace(), state.key());
    Instant now = Instant.now();
    if (existing.isEmpty()) {
      if (expectedRevision != 0) {
        throw new RevisionConflictException("UserState", state.key(), expectedRevision, 0);
      }
      insert(state, now);
    } else {
      int affected =
          jdbc.sql(
                  """
                  UPDATE ds_user_state
                  SET value_json = :valueJson, revision = revision + 1, updated_at = :updatedAt
                  WHERE tenant_id = :tenantId AND user_id = :userId
                    AND namespace = :namespace AND state_key = :key
                    AND revision = :expectedRevision
                  """)
              .param("valueJson", state.valueJson())
              .param("updatedAt", SqlTimestamps.toParam(now))
              .param("tenantId", state.tenantId())
              .param("userId", state.userId())
              .param("namespace", state.namespace())
              .param("key", state.key())
              .param("expectedRevision", expectedRevision)
              .update();
      if (affected == 0) {
        throw new RevisionConflictException(
            "UserState", state.key(), expectedRevision, existing.get().revision());
      }
    }
    return find(state.tenantId(), state.userId(), state.namespace(), state.key()).orElseThrow();
  }

  private UserState upsertLastWriteWins(UserState state) {
    Instant now = Instant.now();
    if (updateWithoutRevisionCheck(state, now) == 0) {
      try {
        insert(state, now);
      } catch (DataIntegrityViolationException insertRace) {
        if (updateWithoutRevisionCheck(state, Instant.now()) == 0) {
          throw insertRace;
        }
      }
    }
    return find(state.tenantId(), state.userId(), state.namespace(), state.key()).orElseThrow();
  }

  private int updateWithoutRevisionCheck(UserState state, Instant now) {
    return jdbc.sql(
            """
            UPDATE ds_user_state
            SET value_json = :valueJson, revision = revision + 1, updated_at = :updatedAt
            WHERE tenant_id = :tenantId AND user_id = :userId
              AND namespace = :namespace AND state_key = :key
            """)
        .param("valueJson", state.valueJson())
        .param("updatedAt", SqlTimestamps.toParam(now))
        .param("tenantId", state.tenantId())
        .param("userId", state.userId())
        .param("namespace", state.namespace())
        .param("key", state.key())
        .update();
  }

  private void insert(UserState state, Instant now) {
    jdbc.sql(
            """
            INSERT INTO ds_user_state
              (tenant_id, user_id, namespace, state_key, value_json, revision,
               created_at, updated_at)
            VALUES
              (:tenantId, :userId, :namespace, :key, :valueJson, 0, :createdAt, :updatedAt)
            """)
        .param("tenantId", state.tenantId())
        .param("userId", state.userId())
        .param("namespace", state.namespace())
        .param("key", state.key())
        .param("valueJson", state.valueJson())
        .param("createdAt", SqlTimestamps.toParam(now))
        .param("updatedAt", SqlTimestamps.toParam(now))
        .update();
  }

  @Override
  public void delete(String tenantId, String userId, String namespace, String key) {
    int affected =
        jdbc.sql(
                """
                DELETE FROM ds_user_state
                WHERE tenant_id = :tenantId AND user_id = :userId
                  AND namespace = :namespace AND state_key = :key
                """)
            .param("tenantId", tenantId)
            .param("userId", userId)
            .param("namespace", namespace)
            .param("key", key)
            .update();
    if (affected == 0) {
      throw new NotFoundException("UserState", key);
    }
  }
}
