package io.github.ccweixiao.datastoria.agent.application;

import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.agent.runtime.AgentRuntimeConfig;

/**
 * Validates browser presentation hints and applies them at the server-owned AgentScope boundary.
 */
final class AgentContextOptions {

  private static final Pattern LANGUAGE_TAG =
      Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");
  private static final Set<String> REASONING_LEVELS =
      Set.of("none", "minimal", "low", "medium", "high", "xhigh");

  private AgentContextOptions() {}

  /**
   * Applies whitelisted request hints. {@code maxIters} may only lower the loop bound within the
   * server-configured ceiling; anything invalid keeps the configured default.
   */
  static AgentRuntimeConfig apply(AgentRuntimeConfig config, JsonNode context, int serverMaxIters) {
    if (context == null || !context.isObject()) {
      return config;
    }
    String language = normalizedLanguage(context.path("responseLanguage").asText(null));
    String reasoning = context.path("reasoningLevel").asText(null);
    // Set.of() rejects null in contains(); a request without reasoningLevel keeps the default.
    if (reasoning == null || !REASONING_LEVELS.contains(reasoning)) {
      reasoning = null;
    }
    boolean outputReasoning =
        !context.has("outputReasoning") || context.path("outputReasoning").asBoolean(true);
    return config
        .withRequestOptions(
            appendLanguagePolicy(config.systemPrompt(), language), reasoning, outputReasoning)
        .withMaxIters(resolvedMaxIters(config, context, serverMaxIters));
  }

  private static int resolvedMaxIters(
      AgentRuntimeConfig config, JsonNode context, int serverMaxIters) {
    int ceiling = Math.max(1, serverMaxIters);
    JsonNode requested = context.path("maxIters");
    if (requested.isIntegralNumber() && requested.canConvertToInt() && requested.asInt() >= 1) {
      return Math.min(requested.asInt(), ceiling);
    }
    return Math.min(config.maxIters(), ceiling);
  }

  private static String normalizedLanguage(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= 35 && LANGUAGE_TAG.matcher(trimmed).matches() ? trimmed : null;
  }

  private static String appendLanguagePolicy(String prompt, String language) {
    if (language == null
        || language.equalsIgnoreCase("en")
        || language.regionMatches(true, 0, "en-", 0, 3)) {
      return prompt;
    }
    return prompt
        + "\n\n## Response Language Policy\n"
        + "- Response language (BCP-47): "
        + language
        + "\n- Write all explanatory prose, headings, visible reasoning summaries, thinking "
        + "summaries, and planning notes in this language.\n"
        + "- Keep SQL, code, error codes, identifiers, and setting names unchanged.";
  }
}
