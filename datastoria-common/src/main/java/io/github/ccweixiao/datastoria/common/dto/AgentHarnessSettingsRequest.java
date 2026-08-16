package io.github.ccweixiao.datastoria.common.dto;

/**
 * Admin-managed agent harness runtime overrides. {@code null} means "keep the process default" for
 * that knob; provided values are clamped to the absolute bounds enforced server-side (iterations
 * 1–100, eviction at least 2048 chars, trigger ratio 0.1–0.95, fallback window at least 8192
 * tokens).
 */
public record AgentHarnessSettingsRequest(
    Integer maxIters,
    Integer toolResultEvictionChars,
    Double compactionTriggerRatio,
    Integer compactionFallbackContextTokens) {

  public static AgentHarnessSettingsRequest empty() {
    return new AgentHarnessSettingsRequest(null, null, null, null);
  }
}
