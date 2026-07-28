package io.github.ccweixiao.datastoria.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import io.github.ccweixiao.datastoria.agent.runtime.AgentScopeCheckpointAdapter;
import io.github.ccweixiao.datastoria.agent.runtime.JsonCheckpointCodec;
import io.github.ccweixiao.datastoria.boot.TestDbHelper;
import io.github.ccweixiao.datastoria.common.agent.CheckpointContent;
import io.github.ccweixiao.datastoria.common.agent.CheckpointType;

/**
 * Wires the {@link AgentScopeCheckpointAdapter} output through {@link CheckpointStore} into the
 * SQLite {@code ds_agent_checkpoint} table and back: round-trip integrity, and tenant isolation on
 * reads. Verifies the stored blob carries no prompt/secret even when the source state did.
 */
@SpringBootTest
@ActiveProfiles("test")
class CheckpointStoreTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final Instant NOW = Instant.parse("2026-07-25T09:00:00Z");

  @Autowired CheckpointStore store;
  @Autowired JdbcClient jdbc;
  @Autowired TestDbHelper dbHelper;

  private final AgentScopeCheckpointAdapter adapter =
      new AgentScopeCheckpointAdapter(new JsonCheckpointCodec());

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    insertSession("sess_main", TENANT, USER);
    insertRun("run_main", TENANT, USER, "sess_main");
  }

  @Test
  void savedCheckpointRoundTripsThroughRepo() {
    AgentState state = sensitiveState();
    CheckpointContent content = adapter.checkpoint(state);

    store.save(TENANT, "run_main", 1, CheckpointType.RUN_STATE, content);

    CheckpointContent loaded = store.loadLatest(TENANT, "run_main").orElseThrow();
    assertThat(loaded.stateJson()).isEqualTo(content.stateJson());
    assertThat(loaded.codecVersion()).isEqualTo(content.codecVersion());
    assertThat(loaded.checksum()).isEqualTo(content.checksum());

    // Even though the source AgentState carried a prompt+secret in its context, the persisted blob
    // does not.
    assertThat(loaded.stateJson())
        .doesNotContain("my secret prompt")
        .doesNotContain("sk-SECRET-123");
  }

  @Test
  void overwriteAtSameSequenceReplacesState() {
    CheckpointContent first = adapter.checkpoint(agentState("sess_main", "secret-summary-1", 1));
    CheckpointContent second = adapter.checkpoint(agentState("sess_main", "secret-summary-2", 2));
    store.save(TENANT, "run_main", 1, CheckpointType.RUN_STATE, first);
    store.save(TENANT, "run_main", 1, CheckpointType.RUN_STATE, second); // overwrite seq 1

    CheckpointContent loaded = store.loadLatest(TENANT, "run_main").orElseThrow();
    assertThat(loaded.stateJson()).isEqualTo(second.stateJson());
    assertThat(loaded.stateJson())
        .contains("\"currentIteration\":2")
        .doesNotContain("secret-summary-1", "secret-summary-2", "summary");
  }

  @Test
  void loadLatestIsEmptyAcrossTenants() {
    CheckpointContent content = adapter.checkpoint(sensitiveState());
    store.save(TENANT, "run_main", 1, CheckpointType.RUN_STATE, content);

    // Same run id, wrong tenant -> empty (tenant isolation enforced by the repository).
    assertThat(store.loadLatest("tenant-other", "run_main")).isEmpty();
    // Unknown run -> empty.
    assertThat(store.loadLatest(TENANT, "run_none")).isEmpty();
  }

  @Test
  void restoreFromPersistedContentRebuildsSafeState() {
    AgentState state = sensitiveState();
    store.save(TENANT, "run_main", 1, CheckpointType.RUN_STATE, adapter.checkpoint(state));

    CheckpointContent loaded = store.loadLatest(TENANT, "run_main").orElseThrow();
    AgentState restored = (AgentState) adapter.restore(loaded);

    assertThat(restored.getSessionId()).isEqualTo("sess-1");
    assertThat(restored.getCurIter()).isEqualTo(3);
    assertThat(restored.getSummary()).isEmpty();
    assertThat(restored.getContext()).isEmpty();
  }

  // ---- helpers ----

  private static AgentState sensitiveState() {
    Msg secretMessage =
        Msg.builder().role(MsgRole.USER).textContent("my secret prompt sk-SECRET-123").build();
    return AgentState.builder()
        .sessionId("sess-1")
        .userId(USER)
        .replyId("reply-1")
        .curIter(3)
        .summary("summary contains prompt sk-SUMMARY-SECRET")
        .shutdownInterrupted(false)
        .context(List.of(secretMessage))
        .build();
  }

  private static AgentState agentState(String sessionId, String summary, int iter) {
    return AgentState.builder()
        .sessionId(sessionId)
        .userId(USER)
        .curIter(iter)
        .summary(summary)
        .build();
  }

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
}
