package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.FeedbackEvent;
import io.datastoria.server.repository.FeedbackEventRepository;

@Repository
public class JdbcFeedbackEventRepository implements FeedbackEventRepository {

  private static final RowMapper<FeedbackEvent> MAPPER =
      (rs, rowNum) ->
          new FeedbackEvent(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("user_id"),
              rs.getString("source"),
              rs.getString("session_id"),
              rs.getString("message_id"),
              rs.getInt("solved") != 0,
              rs.getString("reason_code"),
              rs.getString("payload_json"),
              rs.getString("free_text"),
              rs.getInt("recovery_action_taken") != 0,
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcFeedbackEventRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public FeedbackEvent upsert(FeedbackEvent e) {
    Instant now = Instant.now();
    Optional<FeedbackEvent> existing =
        find(e.tenantId(), e.userId(), e.source(), e.sessionId(), e.messageId());
    if (existing.isEmpty()) {
      jdbc.sql(
              "INSERT INTO ds_feedback_event"
                  + " (id, tenant_id, user_id, source, session_id, message_id, solved,"
                  + " reason_code, payload_json, free_text, recovery_action_taken,"
                  + " created_at, updated_at)"
                  + " VALUES (:id, :tenantId, :userId, :source, :sessionId, :messageId,"
                  + " :solved, :reasonCode, :payloadJson, :freeText,"
                  + " :recoveryActionTaken, :now, :now)")
          .param("id", e.id())
          .param("tenantId", e.tenantId())
          .param("userId", e.userId())
          .param("source", e.source())
          .param("sessionId", e.sessionId())
          .param("messageId", e.messageId())
          .param("solved", e.solved())
          .param("reasonCode", e.reasonCode())
          .param("payloadJson", e.payloadJson())
          .param("freeText", e.freeText())
          .param("recoveryActionTaken", e.recoveryActionTaken())
          .param("now", SqlTimestamps.toParamMillis(now))
          .update();
    } else {
      jdbc.sql(
              "UPDATE ds_feedback_event SET solved = :solved, reason_code = :reasonCode,"
                  + " payload_json = :payloadJson, free_text = :freeText,"
                  + " recovery_action_taken = :recoveryActionTaken, updated_at = :now"
                  + " WHERE id = :id AND tenant_id = :tenantId")
          .param("solved", e.solved())
          .param("reasonCode", e.reasonCode())
          .param("payloadJson", e.payloadJson())
          .param("freeText", e.freeText())
          .param("recoveryActionTaken", e.recoveryActionTaken())
          .param("now", SqlTimestamps.toParamMillis(now))
          .param("id", existing.get().id())
          .param("tenantId", e.tenantId())
          .update();
    }
    return find(e.tenantId(), e.userId(), e.source(), e.sessionId(), e.messageId())
        .orElseThrow(() -> new IllegalStateException("upserted feedback row not found"));
  }

  @Override
  public Optional<FeedbackEvent> find(
      String tenantId, String userId, String source, String sessionId, String messageId) {
    return jdbc.sql(
            "SELECT * FROM ds_feedback_event"
                + " WHERE tenant_id = :tenantId AND user_id = :userId AND source = :source"
                + " AND session_id = :sessionId AND message_id = :messageId")
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("source", source)
        .param("sessionId", sessionId)
        .param("messageId", messageId)
        .query(MAPPER)
        .optional();
  }
}
