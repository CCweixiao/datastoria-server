package io.datastoria.server.agent.runtime;

import io.agentscope.core.state.State;
import io.datastoria.server.agent.domain.CheckpointContent;

/**
 * Bridges an AgentScope {@link State} and DataStoria's {@link CheckpointContent}. This is the ONLY
 * component outside AgentScope's own code that touches {@code io.agentscope.core.state.State}; all
 * domain, repository, and controller code depends solely on {@code CheckpointContent}.
 *
 * <p>Implementations extract only the safe control fields from the runtime state and MUST exclude
 * the conversation {@code context} (which carries the prompt) and any credential, so the serialized
 * checkpoint contains neither prompt nor secret.
 */
public interface CheckpointStateAdapter {

  /** Serializes the runtime state into an integrity-bound, leak-free {@link CheckpointContent}. */
  CheckpointContent checkpoint(State state);

  /** Reconstructs a runtime state from a {@link CheckpointContent} (for run resume in P4.8). */
  State restore(CheckpointContent content);
}
