package io.github.ccweixiao.datastoria.agent.runtime;

/**
 * Resolved runtime options for a run, including request-scoped presentation/reasoning policy. The
 * numeric harness knobs are resolved per run from {@link AgentHarnessSettings} (process defaults
 * merged with the tenant-level admin overrides), never from the request.
 */
public record AgentRuntimeConfig(
    String systemPrompt,
    int maxIters,
    int toolResultEvictionChars,
    double compactionTriggerRatio,
    int compactionFallbackContextTokens,
    String reasoningEffort,
    boolean outputReasoning) {

  public AgentRuntimeConfig(String systemPrompt, int maxIters) {
    this(systemPrompt, maxIters, AgentHarnessSettings.defaults());
  }

  public AgentRuntimeConfig(String systemPrompt, int maxIters, AgentHarnessSettings settings) {
    this(
        systemPrompt,
        maxIters,
        settings.toolResultEvictionChars(),
        settings.compactionTriggerRatio(),
        settings.compactionFallbackContextTokens(),
        null,
        true);
  }

  public AgentRuntimeConfig {
    if (maxIters < 1) {
      maxIters = 1;
    }
    toolResultEvictionChars = Math.max(2_048, toolResultEvictionChars);
    compactionTriggerRatio = Math.min(0.95, Math.max(0.1, compactionTriggerRatio));
    compactionFallbackContextTokens = Math.max(8_192, compactionFallbackContextTokens);
  }

  /** Minimal config: a system prompt and the default loop bound. */
  public static AgentRuntimeConfig minimal(String systemPrompt) {
    return minimal(systemPrompt, AgentHarnessSettings.defaults());
  }

  /** Minimal config with the tenant-effective harness settings. */
  public static AgentRuntimeConfig minimal(String systemPrompt, AgentHarnessSettings settings) {
    return new AgentRuntimeConfig(
        systemPrompt,
        settings.maxIters(),
        settings.toolResultEvictionChars(),
        settings.compactionTriggerRatio(),
        settings.compactionFallbackContextTokens(),
        null,
        true);
  }

  /**
   * Compaction token threshold for this run: the model's context window (or the fallback when the
   * model does not report one) times the trigger ratio.
   */
  public int compactionTriggerTokens(int modelContextWindow) {
    int window = modelContextWindow > 0 ? modelContextWindow : compactionFallbackContextTokens;
    return Math.max(1_000, (int) (window * compactionTriggerRatio));
  }

  public AgentRuntimeConfig withRequestOptions(
      String requestSystemPrompt, String requestReasoningEffort, boolean requestOutputReasoning) {
    return new AgentRuntimeConfig(
        requestSystemPrompt,
        maxIters,
        toolResultEvictionChars,
        compactionTriggerRatio,
        compactionFallbackContextTokens,
        requestReasoningEffort,
        requestOutputReasoning);
  }
}
