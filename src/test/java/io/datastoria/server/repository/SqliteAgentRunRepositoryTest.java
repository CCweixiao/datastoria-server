package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.IllegalRunTransitionException;
import io.datastoria.server.agent.domain.RunFailureCode;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.api.error.NotFoundException;

/**
 * SQLite contract for {@link AgentRunRepository}: round-trip, tenant isolation, idempotency-key
 * lookup, the run state machine (legal / illegal / terminal-idempotent transitions),
 * optimistic-lock no-overwrite under concurrent terminal transitions, and the observer-driven
 * cancel path.
 */
@SpringBootTest
@ActiveProfiles("test")
class SqliteAgentRunRepositoryTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Autowired AgentRunRepository repo;
  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    insertSession("sess_main", TENANT, USER);
  }

  @Test
  void createAndFindRoundTrip() {
    repo.create(newRunningRun("run_a"));
    AgentRun found = repo.find(TENANT, "run_a").orElseThrow();
    assertThat(found.status()).isEqualTo(AgentRunStatus.RUNNING);
    assertThat(found.tenantId()).isEqualTo(TENANT);
    assertThat(found.userId()).isEqualTo(USER);
    assertThat(found.revision()).isZero();
    assertThat(found.createdAt()).isNotNull();
  }

  @Test
  void findExcludesOtherTenant() {
    insertSession("sess_other", "tenant-other", USER);
    repo.create(newRunningRun("run_b", "tenant-other", USER, "sess_other"));
    assertThat(repo.find(TENANT, "run_b")).isEmpty();
    assertThat(repo.find("tenant-other", "run_b")).isPresent();
  }

  @Test
  void findByIdempotencyKeyIsTenantUserScoped() {
    repo.create(newRunningRun("run_c", TENANT, USER, "sess_main", "key-1"));
    assertThat(repo.findByIdempotencyKey(TENANT, USER, "key-1")).isPresent();
    // Same key under a different user is a distinct run.
    insertSession("sess_u2", TENANT, "other@example.com");
    repo.create(newRunningRun("run_c2", TENANT, "other@example.com", "sess_u2", "key-1"));
    assertThat(repo.findByIdempotencyKey(TENANT, "other@example.com", "key-1"))
        .isPresent()
        .map(AgentRun::id)
        .hasValue("run_c2");
    // Cross-tenant lookup sees nothing.
    assertThat(repo.findByIdempotencyKey("tenant-other", USER, "key-1")).isEmpty();
  }

  @Test
  void findBySessionReturnsRunsOldestFirst() {
    repo.create(newRunningRun("r1"));
    repo.create(newRunningRun("r2"));
    repo.create(newRunningRun("r3"));
    var runs = repo.findBySession(TENANT, "sess_main");
    assertThat(runs).extracting(AgentRun::id).containsExactly("r1", "r2", "r3");
  }

  @Test
  void legalTransitionsSucceed() {
    repo.create(newRunningRun("run_ok"));
    assertThat(
            repo.transition(
                TENANT,
                "run_ok",
                AgentRunStatus.SUCCEEDED,
                RunTransition.completing(NOW, "{\"out\":7}")))
        .isTrue();
    AgentRun done = repo.find(TENANT, "run_ok").orElseThrow();
    assertThat(done.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(done.revision()).isEqualTo(1L);
    assertThat(done.finishedAt()).isEqualTo(NOW);
    assertThat(done.usageJson()).contains("out");

    // failed and cancelled from a fresh running run
    repo.create(newRunningRun("run_fail"));
    repo.transition(
        TENANT,
        "run_fail",
        AgentRunStatus.FAILED,
        RunTransition.failing(NOW, RunFailureCode.MODEL_UNAVAILABLE));
    assertThat(repo.find(TENANT, "run_fail").orElseThrow().status())
        .isEqualTo(AgentRunStatus.FAILED);

    repo.create(newRunningRun("run_cancel"));
    repo.transition(TENANT, "run_cancel", AgentRunStatus.CANCELLED, RunTransition.cancelling(NOW));
    AgentRun cancelled = repo.find(TENANT, "run_cancel").orElseThrow();
    assertThat(cancelled.status()).isEqualTo(AgentRunStatus.CANCELLED);
    assertThat(cancelled.finishedAt()).isEqualTo(NOW);
    assertThat(cancelled.errorCode()).isNull();
    assertThat(cancelled.safeMessage()).isNull();
  }

  @Test
  void queuedToRunningTransition() {
    repo.create(newRun("run_q", TENANT, USER, "sess_main", AgentRunStatus.QUEUED, "key-q"));
    assertThat(
            repo.transition(TENANT, "run_q", AgentRunStatus.RUNNING, RunTransition.starting(NOW)))
        .isTrue();
    AgentRun running = repo.find(TENANT, "run_q").orElseThrow();
    assertThat(running.status()).isEqualTo(AgentRunStatus.RUNNING);
    assertThat(running.startedAt()).isEqualTo(NOW);
  }

  @Test
  void illegalTerminalTransitionsRejected() {
    repo.create(newRunningRun("run_t"));
    repo.transition(TENANT, "run_t", AgentRunStatus.SUCCEEDED, RunTransition.completing(NOW, null));

    // Terminal cannot return to running.
    assertThatThrownBy(
            () ->
                repo.transition(
                    TENANT, "run_t", AgentRunStatus.RUNNING, RunTransition.starting(NOW)))
        .isInstanceOf(IllegalRunTransitionException.class);
    // Terminal cannot move to a different terminal outcome.
    assertThatThrownBy(
            () ->
                repo.transition(
                    TENANT,
                    "run_t",
                    AgentRunStatus.FAILED,
                    RunTransition.failing(NOW, RunFailureCode.AGENT_INTERNAL)))
        .isInstanceOf(IllegalRunTransitionException.class);
    assertThatThrownBy(
            () ->
                repo.transition(
                    TENANT, "run_t", AgentRunStatus.CANCELLED, RunTransition.cancelling(NOW)))
        .isInstanceOf(IllegalRunTransitionException.class);

    // Row is untouched: still succeeded at revision 1.
    AgentRun untouched = repo.find(TENANT, "run_t").orElseThrow();
    assertThat(untouched.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(untouched.revision()).isEqualTo(1L);
  }

  @Test
  void terminalTransitionIsIdempotent() {
    repo.create(newRunningRun("run_idem"));
    repo.transition(
        TENANT, "run_idem", AgentRunStatus.SUCCEEDED, RunTransition.completing(NOW, null));
    long revBefore = repo.find(TENANT, "run_idem").orElseThrow().revision();

    // Re-asserting the same terminal status is a no-op success and does not bump revision.
    assertThat(
            repo.transition(
                TENANT, "run_idem", AgentRunStatus.SUCCEEDED, RunTransition.completing(NOW, null)))
        .isTrue();
    AgentRun after = repo.find(TENANT, "run_idem").orElseThrow();
    assertThat(after.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(after.revision()).isEqualTo(revBefore);
  }

  @Test
  void transitionThrowsNotFoundForMissingOrCrossTenant() {
    assertThatThrownBy(
            () ->
                repo.transition(
                    TENANT, "nope", AgentRunStatus.SUCCEEDED, RunTransition.completing(NOW, null)))
        .isInstanceOf(NotFoundException.class);
    // Run exists but belongs to another tenant.
    insertSession("sess_other", "tenant-other", USER);
    repo.create(newRunningRun("run_xo", "tenant-other", USER, "sess_other"));
    assertThatThrownBy(
            () ->
                repo.transition(
                    TENANT,
                    "run_xo",
                    AgentRunStatus.SUCCEEDED,
                    RunTransition.completing(NOW, null)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void concurrentTerminalTransitionsDoNotOverwrite() throws Exception {
    repo.create(newRunningRun("run_race"));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    // One side completes, the other cancels — exactly one terminal may land.
    Callable<Outcome> complete =
        () -> {
          start.await();
          try {
            repo.transition(
                TENANT, "run_race", AgentRunStatus.SUCCEEDED, RunTransition.completing(NOW, null));
            return new Outcome(true, false);
          } catch (IllegalRunTransitionException e) {
            return new Outcome(false, true);
          }
        };
    Callable<Outcome> cancel =
        () -> {
          start.await();
          try {
            repo.transition(
                TENANT, "run_race", AgentRunStatus.CANCELLED, RunTransition.cancelling(NOW));
            return new Outcome(true, false);
          } catch (IllegalRunTransitionException e) {
            return new Outcome(false, true);
          }
        };
    Future<Outcome> f1 = pool.submit(complete);
    Future<Outcome> f2 = pool.submit(cancel);
    start.countDown();
    Outcome o1 = f1.get(10, TimeUnit.SECONDS);
    Outcome o2 = f2.get(10, TimeUnit.SECONDS);
    pool.shutdownNow();

    // Exactly one transition landed; the other was rejected. No terminal overwrote the other.
    assertThat((o1.transitioned() ? 1 : 0) + (o2.transitioned() ? 1 : 0)).isEqualTo(1);
    assertThat((o1.rejected() ? 1 : 0) + (o2.rejected() ? 1 : 0)).isEqualTo(1);
    AgentRun after = repo.find(TENANT, "run_race").orElseThrow();
    assertThat(after.status()).isIn(AgentRunStatus.SUCCEEDED, AgentRunStatus.CANCELLED);
    assertThat(after.revision()).isEqualTo(1L); // only one update landed
  }

  @Test
  void applyCancellationTransitionsRunningRunToCancelled() {
    repo.create(newRunningRun("run_cancel_obs"));
    boolean ok = repo.applyCancellation("run_cancel_obs", NOW);
    assertThat(ok).isTrue();
    AgentRun after = repo.find(TENANT, "run_cancel_obs").orElseThrow();
    assertThat(after.status()).isEqualTo(AgentRunStatus.CANCELLED);
    assertThat(after.finishedAt()).isEqualTo(NOW);
  }

  @Test
  void applyCancellationIsIdempotentAndIgnoresUnknownRun() {
    repo.create(newRunningRun("run_cancel_idem"));
    repo.applyCancellation("run_cancel_idem", NOW);
    long rev = repo.find(TENANT, "run_cancel_idem").orElseThrow().revision();
    // Second cancel is a no-op (already cancelled) — no revision bump.
    assertThat(repo.applyCancellation("run_cancel_idem", NOW.plusSeconds(1))).isTrue();
    assertThat(repo.find(TENANT, "run_cancel_idem").orElseThrow().revision()).isEqualTo(rev);
    // Unknown run id is safe (observer may fire after cleanup).
    assertThat(repo.applyCancellation("run_never", NOW)).isFalse();
  }

  @Test
  void applyCancellationCannotResurrectTerminalRun() {
    repo.create(newRunningRun("run_term"));
    repo.transition(
        TENANT, "run_term", AgentRunStatus.SUCCEEDED, RunTransition.completing(NOW, null));
    // A late cancel arriving after success must not flip a succeeded run to cancelled.
    assertThat(repo.applyCancellation("run_term", NOW)).isFalse();
    assertThat(repo.find(TENANT, "run_term").orElseThrow().status())
        .isEqualTo(AgentRunStatus.SUCCEEDED);
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

  private AgentRun newRunningRun(String id) {
    return newRun(id, TENANT, USER, "sess_main", AgentRunStatus.RUNNING, "idem-" + id);
  }

  private AgentRun newRunningRun(String id, String tenant, String user, String session) {
    return newRun(id, tenant, user, session, AgentRunStatus.RUNNING, "idem-" + id);
  }

  private AgentRun newRunningRun(
      String id, String tenant, String user, String session, String idemKey) {
    return newRun(id, tenant, user, session, AgentRunStatus.RUNNING, idemKey);
  }

  private AgentRun newRun(
      String id,
      String tenant,
      String user,
      String session,
      AgentRunStatus status,
      String idemKey) {
    return new AgentRun(
        id,
        tenant,
        user,
        session,
        null,
        "arev-1",
        "mdl-1",
        status,
        idemKey,
        idemKey,
        null,
        null,
        null,
        null,
        null,
        0L,
        status == AgentRunStatus.RUNNING ? NOW : null,
        null,
        NOW,
        NOW);
  }

  private record Outcome(boolean transitioned, boolean rejected) {}
}
