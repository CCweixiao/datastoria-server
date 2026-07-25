package io.datastoria.server.agent.application;

import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.agent.runtime.AgentRuntimeConfig;

/**
 * Validates browser presentation hints and applies them at the server-owned AgentScope boundary.
 */
final class AgentContextOptions {

  private static final Pattern LANGUAGE_TAG =
      Pattern.compile("^[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*$");
  private static final Set<String> REASONING_LEVELS =
      Set.of("none", "minimal", "low", "medium", "high", "xhigh");

  private AgentContextOptions() {}

  static AgentRuntimeConfig apply(AgentRuntimeConfig config, JsonNode context) {
    if (context == null || !context.isObject()) {
      return config;
    }
    String language = normalizedLanguage(context.path("responseLanguage").asText(null));
    String reasoning = context.path("reasoningLevel").asText(null);
    if (!REASONING_LEVELS.contains(reasoning)) {
      reasoning = null;
    }
    boolean outputReasoning =
        !context.has("outputReasoning") || context.path("outputReasoning").asBoolean(true);
    return config.withRequestOptions(
        appendLanguagePolicy(config.systemPrompt(), language), reasoning, outputReasoning);
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
