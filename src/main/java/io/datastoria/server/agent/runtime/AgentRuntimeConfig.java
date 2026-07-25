package io.datastoria.server.agent.runtime;

/** Resolved runtime options for a run: the pinned system prompt and ReAct loop bound. */
public record AgentRuntimeConfig(String systemPrompt, int maxIters) {

  public AgentRuntimeConfig {
    if (maxIters < 1) {
      maxIters = 1;
    }
  }

  /** P4 minimal config: a system prompt and the default loop bound of 3. */
  public static AgentRuntimeConfig minimal(String systemPrompt) {
    return new AgentRuntimeConfig(systemPrompt == null ? "" : systemPrompt, 3);
  }
}
