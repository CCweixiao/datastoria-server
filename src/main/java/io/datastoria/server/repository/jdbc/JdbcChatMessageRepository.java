package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.domain.ChatMessage;
import io.datastoria.server.repository.ChatMessageRepository;

@Repository
public class JdbcChatMessageRepository implements ChatMessageRepository {

  private static final RowMapper<ChatMessage> MAPPER =
      (rs, rowNum) ->
          new ChatMessage(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("session_id"),
              rs.getString("user_id"),
              rs.getString("role"),
              rs.getString("parts_json"),
              rs.getString("metadata_json"),
              rs.getLong("sequence"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcChatMessageRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ChatMessage save(ChatMessage m) {
    Instant now = Instant.now();
    // Lookup-then-upsert to avoid relying on Spring's exception translation of SQLite's
    // SQLITE_CONSTRAINT error (Xerial does not map it to DataIntegrityViolationException).
    Optional<ChatMessage> existing = findById(m.id(), m.tenantId(), m.sessionId());
    if (existing.isEmpty()) {
      jdbc.sql(
              "INSERT INTO ds_chat_message"
                  + " (id, tenant_id, session_id, user_id, role, parts_json, metadata_json,"
                  + " sequence, created_at, updated_at)"
                  + " VALUES (:id, :tenantId, :sessionId, :userId, :role, :partsJson,"
                  + " :metadataJson, :sequence, :now, :now)")
          .param("id", m.id())
          .param("tenantId", m.tenantId())
          .param("sessionId", m.sessionId())
          .param("userId", m.userId())
          .param("role", m.role())
          .param("partsJson", m.partsJson())
          .param("metadataJson", m.metadataJson())
          .param("sequence", m.sequence())
          .param("now", SqlTimestamps.toParamMillis(now))
          .update();
    } else {
      jdbc.sql(
              "UPDATE ds_chat_message SET role = :role, parts_json = :partsJson,"
                  + " metadata_json = :metadataJson, sequence = :sequence,"
                  + " updated_at = :now"
                  + " WHERE tenant_id = :tenantId AND session_id = :sessionId AND id = :id")
          .param("role", m.role())
          .param("partsJson", m.partsJson())
          .param("metadataJson", m.metadataJson())
          .param("sequence", m.sequence())
          .param("now", SqlTimestamps.toParamMillis(now))
          .param("tenantId", m.tenantId())
          .param("sessionId", m.sessionId())
          .param("id", m.id())
          .update();
    }
    return findById(m.id(), m.tenantId(), m.sessionId())
        .orElseThrow(() -> new NotFoundException("ChatMessage", m.id()));
  }

  @Override
  public Optional<ChatMessage> findById(String id, String tenantId, String sessionId) {
    return jdbc.sql(
            "SELECT * FROM ds_chat_message"
                + " WHERE id = :id AND tenant_id = :tenantId AND session_id = :sessionId")
        .param("id", id)
        .param("tenantId", tenantId)
        .param("sessionId", sessionId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<ChatMessage> findBySession(String sessionId, String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_chat_message"
                + " WHERE tenant_id = :tenantId AND session_id = :sessionId"
                + " ORDER BY sequence ASC, id ASC")
        .param("tenantId", tenantId)
        .param("sessionId", sessionId)
        .query(MAPPER)
        .list();
  }

  @Override
  public boolean exists(String tenantId, String userId, String sessionId, String messageId) {
    Long count =
        jdbc.sql(
                "SELECT COUNT(*) FROM ds_chat_message"
                    + " WHERE tenant_id = :tenantId AND user_id = :userId"
                    + " AND session_id = :sessionId AND id = :messageId")
            .param("tenantId", tenantId)
            .param("userId", userId)
            .param("sessionId", sessionId)
            .param("messageId", messageId)
            .query(Long.class)
            .single();
    return count > 0;
  }
}
