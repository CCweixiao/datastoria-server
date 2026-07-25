package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.domain.AgentCheckpoint;
import io.datastoria.server.agent.domain.CheckpointType;

/**
 * SQLite contract for {@link AgentCheckpointRepository}: append (new sequence), overwrite (same
 * sequence preserves created_at, bumps updated_at), latest-by-max-sequence, ordered reads, and
 * tenant isolation.
 */
@SpringBootTest
@ActiveProfiles("test")
class SqliteAgentCheckpointRepositoryTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Autowired AgentCheckpointRepository repo;
  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    insertSession("sess_main", TENANT, USER);
    insertRun("run_main", TENANT, USER, "sess_main");
  }

  @Test
  void saveAppendsBySequence() {
    repo.save(checkpoint("cp1", TENANT, "run_main", 1, "{\"v\":1}"));
    repo.save(checkpoint("cp2", TENANT, "run_main", 2, "{\"v\":2}"));
    repo.save(checkpoint("cp3", TENANT, "run_main", 3, "{\"v\":3}"));

    var all = repo.findAllByRun(TENANT, "run_main");
    assertThat(all).extracting(AgentCheckpoint::sequence).containsExactly(1L, 2L, 3L);
    assertThat(repo.findLatest(TENANT, "run_main").orElseThrow().sequence()).isEqualTo(3L);
  }

  @Test
  void saveOverwritesAtSameSequencePreservingCreatedAt() {
    repo.save(checkpoint("cp_over", TENANT, "run_main", 1, "{\"v\":1}", "1".repeat(64)));
    AgentCheckpoint first = repo.findBySequence(TENANT, "run_main", 1).orElseThrow();
    assertThat(first.stateJson()).isEqualTo("{\"v\":1}");
    assertThat(first.checksum()).isEqualTo("1".repeat(64));
    Instant createdFirst = first.createdAt();

    // Overwrite at the same (tenant, run, sequence) with new content + checksum, new id.
    repo.save(checkpoint("cp_over2", TENANT, "run_main", 1, "{\"v\":99}", "9".repeat(64)));
    var all = repo.findAllByRun(TENANT, "run_main");
    assertThat(all).hasSize(1); // still a single row at sequence 1
    AgentCheckpoint overwritten = all.get(0);
    assertThat(overwritten.stateJson()).isEqualTo("{\"v\":99}");
    assertThat(overwritten.checksum()).isEqualTo("9".repeat(64));
    assertThat(overwritten.createdAt()).isEqualTo(createdFirst); // created_at preserved
    assertThat(overwritten.updatedAt()).isAfterOrEqualTo(overwritten.createdAt());
  }

  @Test
  void findLatestIsEmptyForUnknownRun() {
    assertThat(repo.findLatest(TENANT, "run_none")).isEmpty();
    assertThat(repo.findBySequence(TENANT, "run_none", 1)).isEmpty();
    assertThat(repo.findAllByRun(TENANT, "run_none")).isEmpty();
  }

  @Test
  void readsAreTenantScoped() {
    insertSession("sess_other", "tenant-other", USER);
    insertRun("run_other", "tenant-other", USER, "sess_other");
    repo.save(checkpoint("cp_other", "tenant-other", "run_other", 1, "{\"v\":1}"));

    // Cross-tenant reads see nothing.
    assertThat(repo.findLatest(TENANT, "run_other")).isEmpty();
    assertThat(repo.findBySequence(TENANT, "run_other", 1)).isEmpty();
    assertThat(repo.findAllByRun(TENANT, "run_other")).isEmpty();
    // Correct tenant sees it.
    assertThat(repo.findLatest("tenant-other", "run_other")).isPresent();
  }

  @Test
  void concurrentSaveAtSameSequenceConvergesWithoutUniqueKeyFailure() throws Exception {
    var start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first =
          pool.submit(
              () -> {
                start.await();
                repo.save(checkpoint("cp_race_1", TENANT, "run_main", 1, "{\"writer\":1}"));
                return null;
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                repo.save(checkpoint("cp_race_2", TENANT, "run_main", 1, "{\"writer\":2}"));
                return null;
              });
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }

    var checkpoints = repo.findAllByRun(TENANT, "run_main");
    assertThat(checkpoints).hasSize(1);
    assertThat(checkpoints.get(0).stateJson()).isIn("{\"writer\":1}", "{\"writer\":2}");
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

  private void insertRun(String id, String tenant, String user, String session) {
    jdbc.sql(
            "INSERT INTO ds_agent_run "
                + "(id, tenant_id, user_id, session_id, agent_revision_id, model_id, status, "
                + " idempotency_key, revision, created_at, updated_at) "
                + "VALUES (:id,:t,:u,:s,'arev','mdl','running',:idem,0,:now,:now)")
        .param("id", id)
        .param("t", tenant)
        .param("u", user)
        .param("s", session)
        .param("idem", "idem-" + id)
        .param("now", NOW.toString())
        .update();
  }

  private AgentCheckpoint checkpoint(
      String id, String tenant, String run, long sequence, String stateJson) {
    return checkpoint(id, tenant, run, sequence, stateJson, "a".repeat(64));
  }

  private AgentCheckpoint checkpoint(
      String id, String tenant, String run, long sequence, String stateJson, String checksum) {
    return new AgentCheckpoint(
        id, tenant, run, sequence, CheckpointType.RUN_STATE, stateJson, "v1", checksum, NOW, NOW);
  }
}
