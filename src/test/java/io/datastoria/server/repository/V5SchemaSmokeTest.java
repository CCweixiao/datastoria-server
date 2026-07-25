package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;

/**
 * Smoke-tests the V5 DDL (ds_agent_run, ds_agent_checkpoint) on SQLite: status CHECK, idempotency
 * uniqueness, checkpoint sequence uniqueness, JSON validity, and FK cascade. SchemaParityTest
 * (Testcontainers) covers the MySQL-equivalent assertions in CI.
 *
 * <p>Like {@link V4SchemaSmokeTest}, assertions are on the raw SQLite constraint message because
 * the Xerial driver does not map native constraint errors to Spring exception types.
 */
@SpringBootTest
@ActiveProfiles("test")
class V5SchemaSmokeTest {

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
  void runInsertAndLookup() {
    insertSession("sess_a", TENANT, USER);
    insertRun("run_a", TENANT, USER, "sess_a", "running", "idem-a");
    Long count =
        jdbc.sql("SELECT COUNT(*) FROM ds_agent_run WHERE tenant_id = :t AND id = :id")
            .param("t", TENANT)
            .param("id", "run_a")
            .query(Long.class)
            .single();
    assertThat(count).isEqualTo(1L);
  }

  @Test
  void runStatusCheckRejectsUnknownValue() {
    insertSession("sess_b", TENANT, USER);
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "INSERT INTO ds_agent_run "
                            + "(id, tenant_id, user_id, session_id, agent_revision_id, model_id, "
                            + " status, revision, created_at, updated_at) "
                            + "VALUES (:id,:t,:u,:s,:ar,:m,:status,0,:now,:now)")
                    .param("id", "run_b")
                    .param("t", TENANT)
                    .param("u", USER)
                    .param("s", "sess_b")
                    .param("ar", "arev")
                    .param("m", "mdl")
                    .param("status", "pausable") // not in the CHECK set
                    .param("now", NOW.toString())
                    .update())
        .hasMessageContaining("CHECK constraint failed");
  }

  @Test
  void runIdempotencyKeyUniquePerTenantUser() {
    insertSession("sess_c", TENANT, USER);
    insertRun("run_c1", TENANT, USER, "sess_c", "running", "shared-key");
    // Same (tenant, user, idempotency_key) under a new run id fails.
    assertThatThrownBy(() -> insertRun("run_c2", TENANT, USER, "sess_c", "running", "shared-key"))
        .hasMessageContaining("UNIQUE constraint failed");
    // A different user may reuse the same idempotency key.
    insertSession("sess_c2", TENANT, "other@example.com");
    insertRun("run_c3", TENANT, "other@example.com", "sess_c2", "running", "shared-key");
  }

  @Test
  void checkpointSequenceUniquePerRun() {
    insertSession("sess_d", TENANT, USER);
    insertRun("run_d", TENANT, USER, "sess_d", "running", "idem-d");
    insertCheckpoint("cp_d1", TENANT, "run_d", 1, "{\"v\":1}");
    // Same (tenant, run, sequence) under a new id fails.
    assertThatThrownBy(() -> insertCheckpoint("cp_d2", TENANT, "run_d", 1, "{\"v\":2}"))
        .hasMessageContaining("UNIQUE constraint failed")
        .hasMessageContaining("sequence");
    // A different run may reuse sequence 1.
    insertRun("run_d2", TENANT, USER, "sess_d", "running", "idem-d2");
    insertCheckpoint("cp_d3", TENANT, "run_d2", 1, "{\"v\":1}");
  }

  @Test
  void checkpointJsonValidityConstraintRejectsInvalidJson() {
    insertSession("sess_e", TENANT, USER);
    insertRun("run_e", TENANT, USER, "sess_e", "running", "idem-e");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "INSERT INTO ds_agent_checkpoint "
                            + "(id, tenant_id, run_id, sequence, checkpoint_type, state_json, "
                            + " codec_version, checksum, created_at, updated_at) "
                            + "VALUES (:id,:t,:r,1,'run_state',:sj,'v1',:checksum,:now,:now)")
                    .param("id", "cp_e")
                    .param("t", TENANT)
                    .param("r", "run_e")
                    .param("sj", "not-json")
                    .param("checksum", "a".repeat(64))
                    .param("now", NOW.toString())
                    .update())
        .hasMessageContaining("CHECK constraint failed")
        .hasMessageContaining("json_valid");
  }

  @Test
  void checkpointRejectsNullStateAndUnknownType() {
    insertSession("sess_type", TENANT, USER);
    insertRun("run_type", TENANT, USER, "sess_type", "running", "idem-type");
    assertThatThrownBy(
            () -> insertCheckpointWithType("cp_null", TENANT, "run_type", 1, "run_state", null))
        .hasMessageContaining("NOT NULL constraint failed");
    assertThatThrownBy(
            () ->
                insertCheckpointWithType(
                    "cp_type", TENANT, "run_type", 1, "unexpected", "{\"v\":1}"))
        .hasMessageContaining("CHECK constraint failed");
  }

  @Test
  void checkpointRequiresSha256Checksum() {
    insertSession("sess_checksum", TENANT, USER);
    insertRun("run_checksum", TENANT, USER, "sess_checksum", "running", "idem-checksum");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        "INSERT INTO ds_agent_checkpoint "
                            + "(id, tenant_id, run_id, sequence, checkpoint_type, state_json, "
                            + " codec_version, checksum, created_at, updated_at) "
                            + "VALUES ('cp_checksum',:t,:r,1,'run_state','{}','v1','bad',:now,:now)")
                    .param("t", TENANT)
                    .param("r", "run_checksum")
                    .param("now", NOW.toString())
                    .update())
        .hasMessageContaining("CHECK constraint failed");
  }

  @Test
  void deletingRunCascadesToCheckpoints() {
    insertSession("sess_f", TENANT, USER);
    insertRun("run_f", TENANT, USER, "sess_f", "running", "idem-f");
    insertCheckpoint("cp_f1", TENANT, "run_f", 1, "{\"v\":1}");
    insertCheckpoint("cp_f2", TENANT, "run_f", 2, "{\"v\":2}");

    int deleted =
        jdbc.sql("DELETE FROM ds_agent_run WHERE tenant_id = :t AND id = :id")
            .param("t", TENANT)
            .param("id", "run_f")
            .update();
    assertThat(deleted).isEqualTo(1);
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM ds_agent_checkpoint WHERE run_id = :r")
                .param("r", "run_f")
                .query(Long.class)
                .single())
        .isZero();
  }

  @Test
  void deletingSessionCascadesToRunsAndCheckpoints() {
    insertSession("sess_g", TENANT, USER);
    insertRun("run_g", TENANT, USER, "sess_g", "running", "idem-g");
    insertCheckpoint("cp_g", TENANT, "run_g", 1, "{\"v\":1}");

    jdbc.sql("DELETE FROM ds_chat_session WHERE tenant_id = :t AND id = :id")
        .param("t", TENANT)
        .param("id", "sess_g")
        .update();
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM ds_agent_run WHERE session_id = :s")
                .param("s", "sess_g")
                .query(Long.class)
                .single())
        .isZero();
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM ds_agent_checkpoint WHERE run_id = :r")
                .param("r", "run_g")
                .query(Long.class)
                .single())
        .isZero();
  }

  // ---- helpers ----

  private void insertSession(String id, String tenant, String user) {
    jdbc.sql(
            "INSERT INTO ds_chat_session "
                + "(id, tenant_id, user_id, connection_id, title, revision, created_at, updated_at) "
                + "VALUES (:id,:t,:u,'ch','t',0,:now,:now)")
        .param("id", id)
        .param("t", tenant)
        .param("u", user)
        .param("now", NOW.toString())
        .update();
  }

  private void insertRun(
      String id, String tenant, String user, String session, String status, String idemKey) {
    jdbc.sql(
            "INSERT INTO ds_agent_run "
                + "(id, tenant_id, user_id, session_id, agent_revision_id, model_id, status, "
                + " idempotency_key, revision, created_at, updated_at) "
                + "VALUES (:id,:t,:u,:s,'arev','mdl',:status,:idem,0,:now,:now)")
        .param("id", id)
        .param("t", tenant)
        .param("u", user)
        .param("s", session)
        .param("status", status)
        .param("idem", idemKey)
        .param("now", NOW.toString())
        .update();
  }

  private void insertCheckpoint(
      String id, String tenant, String run, long sequence, String stateJson) {
    insertCheckpointWithType(id, tenant, run, sequence, "run_state", stateJson);
  }

  private void insertCheckpointWithType(
      String id, String tenant, String run, long sequence, String type, String stateJson) {
    jdbc.sql(
            "INSERT INTO ds_agent_checkpoint "
                + "(id, tenant_id, run_id, sequence, checkpoint_type, state_json, codec_version, "
                + " checksum, created_at, updated_at) "
                + "VALUES (:id,:t,:r,:seq,:type,:sj,'v1',:checksum,:now,:now)")
        .param("id", id)
        .param("t", tenant)
        .param("r", run)
        .param("seq", sequence)
        .param("type", type)
        .param("sj", stateJson)
        .param("checksum", "a".repeat(64))
        .param("now", NOW.toString())
        .update();
  }
}
