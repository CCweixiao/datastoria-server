package io.github.ccweixiao.datastoria.dao.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;

/** Smoke-tests the V4 schema on the project's MySQL 5.7 baseline. */
@SpringBootTest
@ActiveProfiles("dev")
class MysqlV4SchemaSmokeTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void sessionInsertAndLookup() {
    insertSession("sess_a", TENANT, USER, "ch-test", "Title A", NOW);

    Long count =
        jdbc.sql(
                "SELECT COUNT(*) FROM ds_chat_session "
                    + "WHERE tenant_id = :t AND user_id = :u AND id = :id")
            .param("t", TENANT)
            .param("u", USER)
            .param("id", "sess_a")
            .query(Long.class)
            .single();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void sessionPrimaryKeyBlocksGlobalIdCollision() {
    insertSession("sess_dup", TENANT, USER, "ch-test", "T1", NOW);
    // PK is id (globally unique); a second insert under a different tenant with the same id fails.
    assertThatThrownBy(
            () -> insertSession("sess_dup", "tenant-other", USER, "ch-other", "Conflict", NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void messageSequenceUniquePerSession() {
    insertSession("sess_b", TENANT, USER, "ch-test", "B", NOW);
    insertMessage("msg_b1", TENANT, "sess_b", USER, "user", 1, NOW);
    // Same sequence under the same session fails.
    assertThatThrownBy(() -> insertMessage("msg_b2", TENANT, "sess_b", USER, "user", 1, NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
    // Different session allows the same sequence value.
    insertSession("sess_c", TENANT, USER, "ch-test", "C", NOW);
    insertMessage("msg_c1", TENANT, "sess_c", USER, "user", 1, NOW);
  }

  @Test
  void messageIdUniquePerSession() {
    insertSession("sess_d", TENANT, USER, "ch-test", "D", NOW);
    insertMessage("msg_d1", TENANT, "sess_d", USER, "user", 1, NOW);
    // Same id under the same (tenant, session) with a different sequence fails.
    assertThatThrownBy(() -> insertMessage("msg_d1", TENANT, "sess_d", USER, "user", 2, NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void feedbackUpsertKeyUnique() {
    insertSession("sess_e", TENANT, USER, "ch-test", "E", NOW);
    insertMessage("msg_e1", TENANT, "sess_e", USER, "assistant", 1, NOW);
    insertFeedback("fb_e", TENANT, USER, "sess_e", "msg_e1", true, NOW);
    // Same (tenant, user, source, session, message) fails for a new id.
    assertThatThrownBy(() -> insertFeedback("fb_e2", TENANT, USER, "sess_e", "msg_e1", false, NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shareActiveKeyUniquePerSession() {
    insertSession("sess_f", TENANT, USER, "ch-test", "F", NOW);
    insertShare("shr_f1", TENANT, "sess_f", USER, "hash-1", NOW);
    // Same (tenant, session) with another non-revoked row fails on active_key uniqueness.
    assertThatThrownBy(() -> insertShare("shr_f2", TENANT, "sess_f", USER, "hash-2", NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void shareRevocationFreesActiveKey() {
    insertSession("sess_g", TENANT, USER, "ch-test", "G", NOW);
    insertShare("shr_g1", TENANT, "sess_g", USER, "hash-g1", NOW);
    // Revoke it.
    int affected =
        jdbc.sql(
                "UPDATE ds_session_share SET revoked_at = :now "
                    + "WHERE tenant_id = :t AND session_id = :s AND revoked_at IS NULL")
            .param("now", java.sql.Timestamp.from(NOW))
            .param("t", TENANT)
            .param("s", "sess_g")
            .update();
    assertThat(affected).isEqualTo(1);
    // A new active share can now be created for the same session.
    insertShare("shr_g2", TENANT, "sess_g", USER, "hash-g2", NOW.plusSeconds(60));
  }

  @Test
  void shareTokenHashUnique() {
    insertSession("sess_h", TENANT, USER, "ch-test", "H", NOW);
    insertShare("shr_h1", TENANT, "sess_h", USER, "shared-hash", NOW);
    // Re-using the same hash under a different session id fails.
    insertSession("sess_h2", TENANT, USER, "ch-test", "H2", NOW);
    assertThatThrownBy(() -> insertShare("shr_h2", TENANT, "sess_h2", USER, "shared-hash", NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void sessionCascadeDeletesMessagesButKeepsFeedbackAndShares() {
    insertSession("sess_i", TENANT, USER, "ch-test", "I", NOW);
    insertMessage("msg_i1", TENANT, "sess_i", USER, "user", 1, NOW);
    insertMessage("msg_i2", TENANT, "sess_i", USER, "assistant", 2, NOW);
    insertFeedback("fb_i", TENANT, USER, "sess_i", "msg_i2", true, NOW);
    insertShare("shr_i", TENANT, "sess_i", USER, "hash-i", NOW);

    int deleted =
        jdbc.sql("DELETE FROM ds_chat_session WHERE tenant_id = :t AND id = :id")
            .param("t", TENANT)
            .param("id", "sess_i")
            .update();
    assertThat(deleted).isEqualTo(1);

    // Cascade reaches messages. Feedback keeps no session FK since V26 (query-error feedback
    // can reference an ephemeral agent session), so it survives as an audit row like shares.
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM ds_chat_message WHERE session_id = :s")
                .param("s", "sess_i")
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM ds_feedback_event WHERE session_id = :s")
                .param("s", "sess_i")
                .query(Long.class)
                .single())
        .isEqualTo(1);
    // Share has no FK and remains as an audit row.
    List<Map<String, Object>> shares =
        jdbc.sql("SELECT * FROM ds_session_share WHERE session_id = :s")
            .param("s", "sess_i")
            .query()
            .listOfRows();
    assertThat(shares).hasSize(1);
  }

  @Test
  void messageJsonValidityConstraintRejectsInvalidJson() {
    insertSession("sess_j", TENANT, USER, "ch-test", "J", NOW);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "INSERT INTO ds_chat_message "
                            + "(id, tenant_id, session_id, user_id, role, parts_json, sequence, created_at, updated_at) "
                            + "VALUES (:id, :t, :s, :u, :r, :p, :seq, :c, :u2)")
                    .param("id", "msg_j1")
                    .param("t", TENANT)
                    .param("s", "sess_j")
                    .param("u", USER)
                    .param("r", "user")
                    .param("p", "not-json")
                    .param("seq", 1)
                    .param("c", java.sql.Timestamp.from(NOW))
                    .param("u2", java.sql.Timestamp.from(NOW))
                    .update())
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ---- helpers ----

  private void insertSession(
      String id, String tenant, String user, String conn, String title, Instant now) {
    jdbc.sql(
            "INSERT INTO ds_chat_session "
                + "(id, tenant_id, user_id, connection_id, title, revision, created_at, updated_at) "
                + "VALUES (:id, :t, :u, :c, :title, 0, :now, :now)")
        .param("id", id)
        .param("t", tenant)
        .param("u", user)
        .param("c", conn)
        .param("title", title)
        .param("now", java.sql.Timestamp.from(now))
        .update();
  }

  private void insertMessage(
      String id,
      String tenant,
      String session,
      String user,
      String role,
      long sequence,
      Instant now) {
    jdbc.sql(
            "INSERT INTO ds_chat_message "
                + "(id, tenant_id, session_id, user_id, role, parts_json, metadata_json, sequence, "
                + " created_at, updated_at) "
                + "VALUES (:id, :t, :s, :u, :r, :p, NULL, :seq, :now, :now)")
        .param("id", id)
        .param("t", tenant)
        .param("s", session)
        .param("u", user)
        .param("r", role)
        .param("p", "[{\"type\":\"text\",\"text\":\"hi\"}]")
        .param("seq", sequence)
        .param("now", java.sql.Timestamp.from(now))
        .update();
  }

  private void insertFeedback(
      String id,
      String tenant,
      String user,
      String session,
      String message,
      boolean solved,
      Instant now) {
    jdbc.sql(
            "INSERT INTO ds_feedback_event "
                + "(id, tenant_id, user_id, source, session_id, message_id, solved, reason_code, "
                + " payload_json, free_text, recovery_action_taken, created_at, updated_at) "
                + "VALUES (:id, :t, :u, 'auto_explain_error', :s, :m, :solved, NULL, :payload, NULL, 0, :now, :now)")
        .param("id", id)
        .param("t", tenant)
        .param("u", user)
        .param("s", session)
        .param("m", message)
        .param("solved", solved ? 1 : 0)
        .param("payload", "{\"queryId\":\"q_1\"}")
        .param("now", java.sql.Timestamp.from(now))
        .update();
  }

  private void insertShare(
      String id, String tenant, String session, String owner, String tokenHash, Instant now) {
    jdbc.sql(
            "INSERT INTO ds_session_share "
                + "(id, tenant_id, session_id, owner_user_id, token_hash, expires_at, revoked_at, created_at) "
                + "VALUES (:id, :t, :s, :o, :th, :exp, NULL, :now)")
        .param("id", id)
        .param("t", tenant)
        .param("s", session)
        .param("o", owner)
        .param("th", tokenHash)
        .param("exp", java.sql.Timestamp.from(now.plusSeconds(3600)))
        .param("now", java.sql.Timestamp.from(now))
        .update();
  }
}
