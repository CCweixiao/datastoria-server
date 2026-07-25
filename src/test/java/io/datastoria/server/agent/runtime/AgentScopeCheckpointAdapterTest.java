package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.State;
import io.datastoria.server.agent.domain.CheckpointContent;

/**
 * Unit tests for {@link AgentScopeCheckpointAdapter}: the conversation {@code context} (which
 * carries the prompt and any secrets inside messages) is excluded from the serialized checkpoint;
 * summary is excluded as another free-form secret carrier; only safe control scalars are kept.
 */
class AgentScopeCheckpointAdapterTest {

  private final AgentScopeCheckpointAdapter adapter =
      new AgentScopeCheckpointAdapter(new JsonCheckpointCodec());

  private static AgentState stateWithSensitiveContext() {
    Msg secretMessage =
        Msg.builder()
            .role(MsgRole.USER)
            .textContent("my secret prompt with key sk-SECRET-123")
            .build();
    return AgentState.builder()
        .sessionId("sess-1")
        .userId("user-1")
        .replyId("reply-9")
        .curIter(3)
        .summary("summary repeats prompt and key sk-SUMMARY-SECRET")
        .shutdownInterrupted(false)
        .context(List.of(secretMessage))
        .build();
  }

  @Test
  void checkpointExcludesPromptAndSecretsFromContext() {
    CheckpointContent content = adapter.checkpoint(stateWithSensitiveContext());

    String json = content.stateJson();
    assertThat(json).doesNotContain("my secret prompt");
    assertThat(json).doesNotContain("sk-SECRET-123");
    assertThat(json).doesNotContain("context"); // the message list is never serialized
    assertThat(json)
        .doesNotContain("summary repeats prompt")
        .doesNotContain("sk-SUMMARY-SECRET")
        .doesNotContain("summary")
        .contains("\"currentIteration\":3");
  }

  @Test
  void restoreRoundTripsSafeScalarsWithEmptyContext() {
    CheckpointContent content = adapter.checkpoint(stateWithSensitiveContext());

    State restored = adapter.restore(content);
    assertThat(restored).isInstanceOf(AgentState.class);
    AgentState restoredState = (AgentState) restored;
    assertThat(restoredState.getSessionId()).isEqualTo("sess-1");
    assertThat(restoredState.getUserId()).isEqualTo("user-1");
    assertThat(restoredState.getReplyId()).isEqualTo("reply-9");
    assertThat(restoredState.getCurIter()).isEqualTo(3);
    assertThat(restoredState.getSummary()).isEmpty();
    // Context is not restored from the checkpoint (replayed from ds_chat_message in P4.8).
    assertThat(restoredState.getContext()).isEmpty();
  }

  @Test
  void rejectsUnknownStateType() {
    State notAgentState = new State() {};
    assertThatThrownBy(() -> adapter.checkpoint(notAgentState))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
