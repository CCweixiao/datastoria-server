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
import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.PendingActionConflictException;
import io.datastoria.server.agent.domain.PendingActionExpiredException;
import io.datastoria.server.agent.domain.PendingActionResolution;
import io.datastoria.server.agent.domain.PendingActionStatus;
import io.datastoria.server.agent.domain.PendingActionType;
import io.datastoria.server.api.error.NotFoundException;

/** SQLite contract for P8 pending-action ownership, expiry, CAS, and retry semantics. */
@SpringBootTest
@ActiveProfiles("test")
class SqliteAgentPendingActionRepositoryTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

  @Autowired AgentPendingActionRepository repo;
  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper db;

  @BeforeEach
  void setUp() {
    db.cleanAll();
    insertRun("run-1", TENANT, USER);
  }

  @Test
  void questionRoundTripsAndIsOwnerScoped() {
    AgentPendingAction created = repo.create(USER, pending("action-1", PendingActionType.QUESTION));

    assertThat(created.status()).isEqualTo(PendingActionStatus.PENDING);
    assertThat(repo.find(TENANT, USER, "run-1", "action-1")).contains(created);
    assertThat(repo.find(TENANT, "other@example.com", "run-1", "action-1")).isEmpty();
    assertThat(repo.find("other-tenant", USER, "run-1", "action-1")).isEmpty();
  }

  @Test
  void createRejectsCrossUserRun() {
    assertThatThrownBy(
            () -> repo.create("other@example.com", pending("action-x", PendingActionType.QUESTION)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void respondIsCasAndIdenticalRetryIsIdempotent() {
    repo.create(USER, pending("action-2", PendingActionType.QUESTION));
    PendingActionResolution response =
        resolution(PendingActionStatus.RESPONDED, "{\"answers\":{\"q1\":\"yes\"}}", "a");

    AgentPendingAction first = repo.resolve(TENANT, USER, "run-1", "action-2", response);
    AgentPendingAction retry = repo.resolve(TENANT, USER, "run-1", "action-2", response);

    assertThat(first.status()).isEqualTo(PendingActionStatus.RESPONDED);
    assertThat(first.revision()).isEqualTo(1);
    assertThat(retry).isEqualTo(first);
  }

  @Test
  void differentRetryConflictsWithoutOverwrite() {
    repo.create(USER, pending("action-3", PendingActionType.APPROVAL));
    repo.resolve(
        TENANT,
        USER,
        "run-1",
        "action-3",
        resolution(PendingActionStatus.APPROVED, "{\"approved\":true}", "b"));

    assertThatThrownBy(
            () ->
                repo.resolve(
                    TENANT,
                    USER,
                    "run-1",
                    "action-3",
                    resolution(PendingActionStatus.DENIED, "{\"approved\":false}", "c")))
        .isInstanceOf(PendingActionConflictException.class);
    assertThat(repo.find(TENANT, USER, "run-1", "action-3").orElseThrow().status())
        .isEqualTo(PendingActionStatus.APPROVED);
  }

  @Test
  void resolutionKindMustMatchActionKind() {
    repo.create(USER, pending("action-4", PendingActionType.QUESTION));
    assertThatThrownBy(
            () ->
                repo.resolve(
                    TENANT,
                    USER,
                    "run-1",
                    "action-4",
                    resolution(PendingActionStatus.APPROVED, "{\"approved\":true}", "d")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void expiredActionCannotResolve() {
    AgentPendingAction expired =
        new AgentPendingAction(
            "action-5",
            TENANT,
            "run-1",
            "call-action-5",
            PendingActionType.QUESTION,
            "{\"question\":\"Continue?\"}",
            null,
            null,
            PendingActionStatus.PENDING,
            NOW.minusSeconds(1),
            null,
            null,
            0,
            NOW.minusSeconds(60),
            NOW.minusSeconds(60));
    repo.create(USER, expired);

    assertThatThrownBy(
            () ->
                repo.resolve(
                    TENANT,
                    USER,
                    "run-1",
                    "action-5",
                    resolution(PendingActionStatus.RESPONDED, "{\"answer\":\"yes\"}", "e")))
        .isInstanceOf(PendingActionExpiredException.class);
    assertThat(repo.find(TENANT, USER, "run-1", "action-5").orElseThrow().status())
        .isEqualTo(PendingActionStatus.EXPIRED);
  }

  @Test
  void bulkExpiryOnlyTouchesDuePendingRows() {
    repo.create(USER, pending("due", PendingActionType.QUESTION, NOW.minusSeconds(1)));
    repo.create(USER, pending("future", PendingActionType.QUESTION, NOW.plusSeconds(300)));

    assertThat(repo.expireDue(NOW)).isEqualTo(1);
    assertThat(repo.findPending(TENANT, USER, "run-1"))
        .extracting(AgentPendingAction::id)
        .containsExactly("future");
  }

  @Test
  void cascadeDeletesActionsWithRun() {
    repo.create(USER, pending("action-6", PendingActionType.QUESTION));
    jdbc.sql("DELETE FROM ds_agent_run WHERE tenant_id=:tenant AND id='run-1'")
        .param("tenant", TENANT)
        .update();
    assertThat(repo.find(TENANT, USER, "run-1", "action-6")).isEmpty();
  }

  private AgentPendingAction pending(String id, PendingActionType type) {
    return pending(id, type, NOW.plusSeconds(300));
  }

  private AgentPendingAction pending(String id, PendingActionType type, Instant expiresAt) {
    return new AgentPendingAction(
        id,
        TENANT,
        "run-1",
        "call-" + id,
        type,
        type == PendingActionType.QUESTION
            ? "{\"question\":\"Continue?\"}"
            : "{\"toolName\":\"execute_sql\"}",
        null,
        null,
        PendingActionStatus.PENDING,
        expiresAt,
        null,
        null,
        0,
        NOW,
        NOW);
  }

  private PendingActionResolution resolution(
      PendingActionStatus status, String response, String digestCharacter) {
    return new PendingActionResolution(status, response, digestCharacter.repeat(64), USER, NOW);
  }

  private void insertRun(String runId, String tenant, String user) {
    jdbc.sql(
            "INSERT INTO ds_chat_session"
                + " (id,tenant_id,user_id,connection_id,title,revision,created_at,updated_at)"
                + " VALUES ('session-1',:tenant,:user,'ch','test',0,:now,:now)")
        .param("tenant", tenant)
        .param("user", user)
        .param("now", NOW.toString())
        .update();
    jdbc.sql(
            "INSERT INTO ds_agent_run"
                + " (id,tenant_id,user_id,session_id,agent_revision_id,model_id,status,revision,"
                + " created_at,updated_at)"
                + " VALUES (:id,:tenant,:user,'session-1','agent-rev','model','waiting_input',0,"
                + " :now,:now)")
        .param("id", runId)
        .param("tenant", tenant)
        .param("user", user)
        .param("now", NOW.toString())
        .update();
  }
}
