package io.datastoria.server.agent.runtime;

import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.State;
import io.datastoria.server.agent.domain.CheckpointCodec;
import io.datastoria.server.agent.domain.CheckpointContent;
import io.datastoria.server.agent.domain.CheckpointState;

/**
 * {@link CheckpointStateAdapter} for AgentScope {@link AgentState}. Extracts only the safe control
 * scalars (session/user/reply ids, iteration and shutdown flag) into a {@link CheckpointState}.
 * Free-form {@code context}, {@code summary} and metadata are deliberately <b>not</b> serialized:
 * each can carry prompts, tool results or credentials. Conversation replay comes from {@code
 * ds_chat_message}; the checkpoint only resumes agent control state.
 *
 * <p>On {@link #restore}, the {@code context} is left empty: a resuming run (P4.8) repopulates
 * messages from the product message table before the next model call. Permission/tool/task context
 * is out of scope for the P4 minimal checkpoint and is not persisted.
 */
public final class AgentScopeCheckpointAdapter implements CheckpointStateAdapter {

  private final CheckpointCodec codec;

  public AgentScopeCheckpointAdapter(CheckpointCodec codec) {
    this.codec = codec;
  }

  @Override
  public CheckpointContent checkpoint(State state) {
    if (!(state instanceof AgentState agentState)) {
      throw new IllegalArgumentException(
          "Unsupported checkpoint state type: "
              + (state == null ? "null" : state.getClass().getName()));
    }
    CheckpointState safe =
        new CheckpointState(
            agentState.getSessionId(),
            agentState.getUserId(),
            agentState.getReplyId(),
            agentState.getCurIter(),
            agentState.isShutdownInterrupted());
    return codec.encode(safe);
  }

  @Override
  public State restore(CheckpointContent content) {
    CheckpointState state = codec.decode(content);
    return AgentState.builder()
        .sessionId(state.sessionId())
        .userId(state.userId())
        .replyId(state.replyId())
        .curIter(state.currentIteration())
        .shutdownInterrupted(state.shutdownInterrupted())
        .build();
  }
}
