package io.github.ccweixiao.datastoria.agent.runtime;

import java.nio.file.Path;

/**
 * Server-owned harness runtime knobs: the AgentScope data directory (workspace and tool-result
 * offload root), the agent reasoning/tool loop bound, the tool-result eviction threshold, the
 * compaction trigger derived from the model context window, and the graceful-shutdown wait.
 *
 * <p>Two layers: {@code datastoria.agent.*} properties provide the process defaults, and the
 * tenant-level {@code settings.ai.agent.harness} configuration entry (admin-managed in the settings
 * dialog) may override the four runtime knobs per tenant — see {@link
 * #withRuntimeOverrides(Integer, Integer, Double, Integer)}. The data directory and shutdown
 * timeout are process-level only. Requests can never set these values.
 *
 * <p>Defaults keep today's effective behavior: 25 iterations, eviction at 32K chars, compaction at
 * 80% of a 100K-token fallback window (= the framework's previous 80K trigger) when the model does
 * not report its window, and a 20s shutdown wait.
 */
public record AgentHarnessSettings(
    Path dataDir,
    int maxIters,
    int toolResultEvictionChars,
    double compactionTriggerRatio,
    int compactionFallbackContextTokens,
    int shutdownTimeoutSeconds) {

  /** Default on-disk location for AgentScope runtime data (never the CWD). */
  public static final String DEFAULT_DATA_DIR_NAME = ".datastoria.agent";

  public AgentHarnessSettings {
    dataDir =
        dataDir == null ? Path.of(System.getProperty("user.home"), DEFAULT_DATA_DIR_NAME) : dataDir;
    maxIters = Math.min(100, Math.max(1, maxIters));
    toolResultEvictionChars = Math.max(2_048, toolResultEvictionChars);
    compactionTriggerRatio = Math.min(0.95, Math.max(0.1, compactionTriggerRatio));
    compactionFallbackContextTokens = Math.max(8_192, compactionFallbackContextTokens);
    shutdownTimeoutSeconds = Math.max(1, shutdownTimeoutSeconds);
  }

  public static AgentHarnessSettings defaults() {
    return new AgentHarnessSettings(null, 25, 32_768, 0.8, 100_000, 20);
  }

  /**
   * Applies tenant-level overrides from the admin settings page; {@code null} keeps the process
   * default for that knob. Clamping happens in the canonical constructor.
   */
  public AgentHarnessSettings withRuntimeOverrides(
      Integer maxItersOverride,
      Integer toolResultEvictionCharsOverride,
      Double compactionTriggerRatioOverride,
      Integer compactionFallbackContextTokensOverride) {
    return new AgentHarnessSettings(
        dataDir,
        maxItersOverride != null ? maxItersOverride : maxIters,
        toolResultEvictionCharsOverride != null
            ? toolResultEvictionCharsOverride
            : toolResultEvictionChars,
        compactionTriggerRatioOverride != null
            ? compactionTriggerRatioOverride
            : compactionTriggerRatio,
        compactionFallbackContextTokensOverride != null
            ? compactionFallbackContextTokensOverride
            : compactionFallbackContextTokens,
        shutdownTimeoutSeconds);
  }
}
