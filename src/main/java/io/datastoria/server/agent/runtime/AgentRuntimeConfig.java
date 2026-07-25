package io.datastoria.server.agent.runtime;

/** Resolved runtime options for a run, including request-scoped presentation/reasoning policy. */
public record AgentRuntimeConfig(
    String systemPrompt, int maxIters, String reasoningEffort, boolean outputReasoning) {

  public AgentRuntimeConfig(String systemPrompt, int maxIters) {
    this(systemPrompt, maxIters, null, true);
  }

  public AgentRuntimeConfig {
    if (maxIters < 1) {
      maxIters = 1;
    }
  }

  /** P4 minimal config: a system prompt and the default loop bound of 3. */
  public static AgentRuntimeConfig minimal(String systemPrompt) {
    return new AgentRuntimeConfig(systemPrompt == null ? "" : systemPrompt, 3, null, true);
  }

  public AgentRuntimeConfig withRequestOptions(
      String requestSystemPrompt, String requestReasoningEffort, boolean requestOutputReasoning) {
    return new AgentRuntimeConfig(
        requestSystemPrompt, maxIters, requestReasoningEffort, requestOutputReasoning);
  }
}
