package io.github.ccweixiao.datastoria.common.agent;

/**
 * Token usage for a single model call. Mirrors the subset of AgentScope {@code ChatUsage} surfaced
 * by P4.
 */
public record TokenUsage(
    int inputTokens, int outputTokens, int cachedTokens, double totalTimeSeconds) {

  public static TokenUsage zero() {
    return new TokenUsage(0, 0, 0, 0d);
  }
}
