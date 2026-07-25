package io.datastoria.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.agent.application.RunLifecycleRecorder;
import io.datastoria.server.agent.application.RunMessageContext;
import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.CheckpointType;
import io.datastoria.server.agent.domain.PendingActionStatus;
import io.datastoria.server.agent.domain.PendingActionType;
import io.datastoria.server.agent.domain.PersistedAgentFrame;
import io.datastoria.server.repository.AgentCheckpointRepository;
import io.datastoria.server.repository.AgentEventRepository;
import io.datastoria.server.repository.AgentPendingActionRepository;

import reactor.core.publisher.Flux;

/** HTTP contract for P8 owner-scoped run, replay, resolution, and cancellation endpoints. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AgentRunControllerTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

  @Autowired WebTestClient client;
  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper db;
  @Autowired AgentPendingActionRepository actions;
  @Autowired AgentEventRepository events;
  @Autowired AgentCheckpointRepository checkpoints;
  @Autowired RunLifecycleRecorder lifecycleRecorder;

  @BeforeEach
  void setUp() {
    db.cleanAll();
    insertRun();
  }

  @Test
  void getReturnsRunAndPendingActionsButHidesCrossUser() {
    actions.create(USER, pending("question-1", PendingActionType.QUESTION));
    client
        .get()
        .uri("/api/ai/runs/run-1")
        .header("x-datastoria-user-email", USER)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.run.status")
        .isEqualTo("WAITING_INPUT")
        .jsonPath("$.pendingActions[0].id")
        .isEqualTo("question-1");
    client
        .get()
        .uri("/api/ai/runs/run-1")
        .header("x-datastoria-user-email", "other@example.com")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void respondCanonicalizesPayloadAndIdenticalRetryIsStable() {
    actions.create(USER, pending("question-2", PendingActionType.QUESTION));
    String first = respond("{\"response\":{\"z\":2,\"a\":1}}", "answer-1");
    String retry = respond("{\"response\":{\"a\":1,\"z\":2}}", "answer-1");
    assertThat(retry).isEqualTo(first);
  }

  @Test
  void differentApprovalRetryReturnsConflictProblem() {
    actions.create(USER, pending("approval-1", PendingActionType.APPROVAL));
    approval("approve", "{\"reason\":\"safe\"}").expectStatus().isOk();
    approval("deny", "{\"reason\":\"changed\"}")
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("ACTION_ALREADY_RESOLVED");
  }

  @Test
  void missingIdempotencyKeyIsBadRequest() {
    actions.create(USER, pending("question-3", PendingActionType.QUESTION));
    client
        .post()
        .uri("/api/ai/runs/run-1/actions/question-3:respond")
        .header("x-datastoria-user-email", USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"response\":\"yes\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("INVALID_REQUEST");
  }

  @Test
  void eventsFilterAndCancelAreOwnerScoped() {
    events.append(new PersistedAgentFrame("e1", TENANT, "run-1", 1, "one", NOW));
    events.append(new PersistedAgentFrame("e2", TENANT, "run-1", 2, "two", NOW));
    client
        .get()
        .uri("/api/ai/runs/run-1/events?after=1")
        .header("x-datastoria-user-email", USER)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].sequence")
        .isEqualTo(2)
        .jsonPath("$[0].frameText")
        .isEqualTo("two")
        .jsonPath("$[1]")
        .doesNotExist();

    for (int attempt = 0; attempt < 2; attempt++) {
      client
          .post()
          .uri("/api/ai/runs/run-1:cancel")
          .header("x-datastoria-user-email", USER)
          .header("Idempotency-Key", "cancel-1")
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.run.status")
          .isEqualTo("CANCELLED");
    }
  }

  @Test
  void approvalBoundaryAtomicallyPersistsWaitingActionAndCheckpoint() {
    jdbc.sql("UPDATE ds_agent_run SET status='running' WHERE id='run-1'").update();
    AgentRunEvent.ToolApprovalRequired approval =
        new AgentRunEvent.ToolApprovalRequired(
            "run-1",
            7,
            NOW,
            "reply-1",
            java.util.List.of(
                new AgentRunEvent.ToolApproval(
                    "action-runtime", "call-runtime", "execute_sql", "{\"sql\":\"SELECT 1\"}")));

    lifecycleRecorder
        .tap(
            new RunMessageContext(TENANT, "run-1", USER, "session-1", "message-1", "model"),
            Flux.just(approval))
        .blockLast();

    client
        .get()
        .uri("/api/ai/runs/run-1")
        .header("x-datastoria-user-email", USER)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.run.status")
        .isEqualTo("WAITING_INPUT")
        .jsonPath("$.pendingActions[0].id")
        .isEqualTo("action-runtime")
        .jsonPath("$.pendingActions[0].requestJson")
        .value(value -> assertThat(value.toString()).contains("reply-1", "SELECT 1"));
    assertThat(checkpoints.findBySequence(TENANT, "run-1", 7))
        .get()
        .extracting(row -> row.checkpointType())
        .isEqualTo(CheckpointType.PENDING_ACTION);
  }

  private String respond(String body, String key) {
    return client
        .post()
        .uri("/api/ai/runs/run-1/actions/question-2:respond")
        .header("x-datastoria-user-email", USER)
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .returnResult()
        .getResponseBody();
  }

  private WebTestClient.ResponseSpec approval(String decision, String body) {
    return client
        .post()
        .uri("/api/ai/runs/run-1/actions/approval-1:" + decision)
        .header("x-datastoria-user-email", USER)
        .header("Idempotency-Key", "approval-decision")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  private AgentPendingAction pending(String id, PendingActionType type) {
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
        Instant.now().plusSeconds(300),
        null,
        null,
        0,
        NOW,
        NOW);
  }

  private void insertRun() {
    jdbc.sql(
            "INSERT INTO ds_chat_session"
                + " (id,tenant_id,user_id,connection_id,title,revision,created_at,updated_at)"
                + " VALUES ('session-1',:tenant,:user,'ch','test',0,:now,:now)")
        .param("tenant", TENANT)
        .param("user", USER)
        .param("now", NOW.toString())
        .update();
    jdbc.sql(
            "INSERT INTO ds_agent_run"
                + " (id,tenant_id,user_id,session_id,agent_revision_id,model_id,status,revision,"
                + " created_at,updated_at)"
                + " VALUES ('run-1',:tenant,:user,'session-1','agent-rev','model','waiting_input',0,"
                + " :now,:now)")
        .param("tenant", TENANT)
        .param("user", USER)
        .param("now", NOW.toString())
        .update();
  }
}
