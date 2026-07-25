package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.domain.ChatSession;
import io.datastoria.server.repository.ChatSessionRepository;
import io.datastoria.server.repository.SessionPage;

@Repository
public class JdbcChatSessionRepository implements ChatSessionRepository {

  private static final RowMapper<ChatSession> MAPPER =
      (rs, rowNum) ->
          new ChatSession(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("user_id"),
              rs.getString("connection_id"),
              rs.getString("title"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcChatSessionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ChatSession save(ChatSession s) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_chat_session"
                + " (id, tenant_id, user_id, connection_id, title, revision, created_at, updated_at)"
                + " VALUES (:id, :tenantId, :userId, :connectionId, :title, 0, :now, :now)")
        .param("id", s.id())
        .param("tenantId", s.tenantId())
        .param("userId", s.userId())
        .param("connectionId", s.connectionId())
        .param("title", s.title())
        .param("now", SqlTimestamps.toParamMillis(now))
        .update();
    return findById(s.id(), s.tenantId(), s.userId())
        .orElseThrow(() -> new NotFoundException("ChatSession", s.id()));
  }

  @Override
  public Optional<ChatSession> findById(String id, String tenantId, String userId) {
    return jdbc.sql(
            "SELECT * FROM ds_chat_session"
                + " WHERE id = :id AND tenant_id = :tenantId AND user_id = :userId")
        .param("id", id)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public SessionPage findPage(
      String tenantId, String userId, String connectionId, SessionListCursor cursor, int limit) {
    // Build the WHERE/ORDER BY with optional filters so we never bind NULL into a comparison.
    StringBuilder sql = new StringBuilder("SELECT * FROM ds_chat_session");
    sql.append(" WHERE tenant_id = :tenantId AND user_id = :userId");
    if (connectionId != null && !connectionId.isBlank()) {
      sql.append(" AND connection_id = :connectionId");
    }
    if (cursor != null) {
      sql.append(" AND (updated_at < :cursorUpdatedAt");
      sql.append(" OR (updated_at = :cursorUpdatedAt AND id < :cursorId))");
    }
    sql.append(" ORDER BY updated_at DESC, id DESC LIMIT :limit");

    var select = jdbc.sql(sql.toString());
    select.param("tenantId", tenantId).param("userId", userId).param("limit", limit + 1);
    if (connectionId != null && !connectionId.isBlank()) {
      select.param("connectionId", connectionId);
    }
    if (cursor != null) {
      select.param("cursorUpdatedAt", SqlTimestamps.toParamMillis(cursor.updatedAt()));
      select.param("cursorId", cursor.sessionId());
    }
    List<ChatSession> all = select.query(MAPPER).list();

    if (all.size() <= limit) {
      return new SessionPage(all, null);
    }
    List<ChatSession> page = new ArrayList<>(all.subList(0, limit));
    ChatSession last = page.get(limit - 1);
    String nextCursor = SessionListCursor.encode(last.updatedAt(), last.id());
    return new SessionPage(page, nextCursor);
  }

  @Override
  public ChatSession rename(String id, String tenantId, String userId, String title) {
    int affected =
        jdbc.sql(
                "UPDATE ds_chat_session SET title = :title, updated_at = :now,"
                    + " revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId AND user_id = :userId")
            .param("title", title)
            .param("now", SqlTimestamps.toParamMillis(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("userId", userId)
            .update();
    if (affected == 0) {
      throw new NotFoundException("ChatSession", id);
    }
    return findById(id, tenantId, userId).orElseThrow();
  }

  @Override
  public void delete(String id, String tenantId, String userId) {
    int affected =
        jdbc.sql(
                "DELETE FROM ds_chat_session"
                    + " WHERE id = :id AND tenant_id = :tenantId AND user_id = :userId")
            .param("id", id)
            .param("tenantId", tenantId)
            .param("userId", userId)
            .update();
    if (affected == 0) {
      throw new NotFoundException("ChatSession", id);
    }
  }

  @Override
  public List<ChatSession> findAllByConnection(
      String tenantId, String userId, String connectionId) {
    return jdbc.sql(
            "SELECT * FROM ds_chat_session"
                + " WHERE tenant_id = :tenantId AND user_id = :userId AND connection_id = :connectionId"
                + " ORDER BY updated_at DESC, id DESC")
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("connectionId", connectionId)
        .query(MAPPER)
        .list();
  }
}
