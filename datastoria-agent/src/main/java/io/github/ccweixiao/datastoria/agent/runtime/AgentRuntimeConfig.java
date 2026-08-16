package io.github.ccweixiao.datastoria.agent.runtime;

/** Resolved runtime options for a run, including request-scoped presentation/reasoning policy. */
public record AgentRuntimeConfig(
    String systemPrompt, int maxIters, String reasoningEffort, boolean outputReasoning) {

  /**
   * Fallback ReAct loop bound when no server ceiling is injected. The effective ceiling comes from
   * {@code datastoria.agent.max-iters} (env {@code DATASTORIA_AGENT_MAX_ITERS}).
   */
  public static final int DEFAULT_MAX_ITERS = 25;

  public AgentRuntimeConfig(String systemPrompt, int maxIters) {
    this(systemPrompt, maxIters, null, true);
  }

  public AgentRuntimeConfig {
    if (maxIters < 1) {
      maxIters = 1;
    }
  }

  /** Minimal config: a system prompt and the default loop bound. */
  public static AgentRuntimeConfig minimal(String systemPrompt) {
    return minimal(systemPrompt, DEFAULT_MAX_ITERS);
  }

  /** Minimal config with the server-configured loop bound. */
  public static AgentRuntimeConfig minimal(String systemPrompt, int maxIters) {
    return new AgentRuntimeConfig(systemPrompt == null ? "" : systemPrompt, maxIters, null, true);
  }

  public AgentRuntimeConfig withRequestOptions(
      String requestSystemPrompt, String requestReasoningEffort, boolean requestOutputReasoning) {
    return new AgentRuntimeConfig(
        requestSystemPrompt, maxIters, requestReasoningEffort, requestOutputReasoning);
  }

  public AgentRuntimeConfig withMaxIters(int bound) {
    return new AgentRuntimeConfig(systemPrompt, bound, reasoningEffort, outputReasoning);
  }
}
