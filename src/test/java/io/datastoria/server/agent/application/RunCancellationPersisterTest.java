package io.datastoria.server.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.repository.AgentRunRepository;

/**
 * Verifies the {@link RunCancellationPersister} observer: feeding it a {@code RunCancelled} (as
 * {@code AgentRunService} does on a CANCEL signal) transitions the run row to {@code cancelled},
 * idempotently, without touching the reactive stream.
 */
@SpringBootTest
@ActiveProfiles("test")
class RunCancellationPersisterTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Autowired AgentRunRepository repo;
  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    jdbc.sql(
            "INSERT INTO ds_chat_session "
                + "(id, tenant_id, user_id, connection_id, title, revision, created_at, updated_at) "
                + "VALUES ('sess_p',:t,:u,'ch','t',0,:now,:now)")
        .param("t", TENANT)
        .param("u", USER)
        .param("now", NOW.toString())
        .update();
    repo.create(
        new AgentRun(
            "run_p",
            TENANT,
            USER,
            "sess_p",
            null,
            "arev",
            "mdl",
            AgentRunStatus.RUNNING,
            "idem-p",
            "idem-p",
            null,
            null,
            null,
            null,
            null,
            0L,
            NOW,
            null,
            NOW,
            NOW));
  }

  @Test
  void observerPersistsCancelled() {
    // Synchronous executor so the write completes before we assert.
    RunCancellationPersister persister = new RunCancellationPersister(repo, Runnable::run);
    persister.accept(new AgentRunEvent.RunCancelled("run_p", 5L, NOW));

    AgentRun after = repo.find(TENANT, "run_p").orElseThrow();
    assertThat(after.status()).isEqualTo(AgentRunStatus.CANCELLED);
    assertThat(after.finishedAt()).isEqualTo(NOW);
  }

  @Test
  void observerIsIdempotentAndSafeForUnknownRun() {
    RunCancellationPersister persister = new RunCancellationPersister(repo, Runnable::run);
    persister.accept(new AgentRunEvent.RunCancelled("run_p", 5L, NOW));
    long rev = repo.find(TENANT, "run_p").orElseThrow().revision();
    // A duplicate (late) cancel must not bump revision or throw.
    persister.accept(new AgentRunEvent.RunCancelled("run_p", 6L, NOW.plusSeconds(1)));
    assertThat(repo.find(TENANT, "run_p").orElseThrow().revision()).isEqualTo(rev);
    // Unknown run id must not throw (observer may fire after cleanup).
    persister.accept(new AgentRunEvent.RunCancelled("run_gone", 1L, NOW));
  }
}
