package io.github.ccweixiao.datastoria.service;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.dto.DiscoveredModelResponse;

/** Normalizes optional model metadata returned by OpenAI-compatible provider catalogs. */
final class ProviderModelMetadata {

  private ProviderModelMetadata() {}

  static DiscoveredModelResponse from(String providerKey, JsonNode node) {
    String id = id(node);
    String normalized = id.toLowerCase(Locale.ROOT);
    String tier = text(node, "tier", "model_tier");
    if (tier == null) {
      tier = inferredTier(normalized);
    }
    Boolean reasoning =
        bool(node, "supports_reasoning", "reasoning")
            || containsAny(normalized, "reasoner", "thinking", "-r1", "glm-z", "qwq");
    Boolean image =
        bool(node, "supports_image_input", "multimodal", "vision")
            || arrayContains(node.path("input_modalities"), "image")
            || arrayContains(node.path("architecture").path("input_modalities"), "image")
            || containsAny(normalized, "-vl", "vision", "omni");
    Integer context =
        positiveInt(
            node, "context_length", "context_window", "context_window_tokens", "inputTokenLimit");
    Integer output =
        positiveInt(node, "max_output_tokens", "max_completion_tokens", "outputTokenLimit");

    String provider = providerKey.toLowerCase(Locale.ROOT);
    if (context == null && "deepseek".equals(provider)) {
      context = normalized.contains("v4") ? 1_000_000 : 65_536;
    } else if (context == null && "minimax".equals(provider) && normalized.contains("m2")) {
      context = 204_800;
    }
    return new DiscoveredModelResponse(
        id, displayName(node, id), providerKey, tier, reasoning, image, context, output);
  }

  static String id(JsonNode node) {
    String id = text(node, "id", "baseModelId", "name");
    if (id == null) {
      return "";
    }
    return id.startsWith("models/") ? id.substring("models/".length()) : id;
  }

  private static String displayName(JsonNode node, String fallback) {
    String value = text(node, "display_name", "displayName");
    return value == null ? fallback : value;
  }

  private static String inferredTier(String id) {
    if (containsAny(id, "flash", "turbo", "lite", "air", "highspeed")) {
      return "fast";
    }
    if (containsAny(id, "-pro", "-max", "ultra", "flagship")) {
      return "flagship";
    }
    if (containsAny(id, "coder", "code", "embedding", "rerank", "vision", "-vl", "omni")) {
      return "specialized";
    }
    return "balanced";
  }

  private static boolean bool(JsonNode node, String... names) {
    for (String name : names) {
      JsonNode value = node.get(name);
      if (value != null && value.isBoolean() && value.booleanValue()) {
        return true;
      }
    }
    return false;
  }

  private static Integer positiveInt(JsonNode node, String... names) {
    for (String name : names) {
      JsonNode value = node.get(name);
      if (value != null && value.canConvertToInt() && value.intValue() > 0) {
        return value.intValue();
      }
    }
    return null;
  }

  private static String text(JsonNode node, String... names) {
    for (String name : names) {
      String value = node.path(name).asText("").trim();
      if (!value.isEmpty()) {
        return value;
      }
    }
    return null;
  }

  private static boolean arrayContains(JsonNode node, String expected) {
    if (!node.isArray()) {
      return false;
    }
    for (JsonNode value : node) {
      if (expected.equalsIgnoreCase(value.asText())) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsAny(String value, String... needles) {
    for (String needle : needles) {
      if (value.contains(needle)) {
        return true;
      }
    }
    return false;
  }
}
