package io.github.ccweixiao.datastoria.agent.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Server-side prompt enrichment for UI mention metadata.
 *
 * <p>The browser only identifies selected schema objects and sends structured metadata. Carrying
 * mention state across turns and converting it into model context belongs to the Java AgentScope
 * boundary, not to frontend prompt manipulation.
 */
final class MentionContextFormatter {

  private static final List<String> KINDS = List.of("database", "table", "setting");
  private static final int MAX_MENTIONS_PER_KIND = 100;
  private static final int MAX_VALUE_LENGTH = 500;

  private final Map<String, List<JsonNode>> active = new LinkedHashMap<>();

  String apply(String userText, JsonNode metadata) {
    update(metadata);
    if (active.isEmpty()) {
      return userText;
    }
    String context = context();
    return context.isBlank() ? userText : userText + "\n\n" + context;
  }

  private void update(JsonNode metadata) {
    JsonNode mentionMetadata = metadata == null ? null : metadata.path("mentionMetadata");
    if (mentionMetadata == null
        || mentionMetadata.path("version").asInt() != 1
        || !mentionMetadata.path("mentions").isArray()) {
      return;
    }
    for (String kind : KINDS) {
      List<JsonNode> mentions =
          java.util.stream.StreamSupport.stream(
                  mentionMetadata.path("mentions").spliterator(), false)
              .filter(node -> kind.equals(node.path("kind").asText()))
              .filter(this::valid)
              .limit(MAX_MENTIONS_PER_KIND)
              .map(node -> (JsonNode) node.deepCopy())
              .toList();
      if (!mentions.isEmpty()) {
        active.put(kind, mentions);
      }
    }
  }

  private boolean valid(JsonNode mention) {
    if (!validText(mention.path("name"))
        || !validText(mention.path("engine"), mention.path("type"))) {
      return false;
    }
    return !mention.hasNonNull("comment")
        || mention.path("comment").asText().length() <= MAX_VALUE_LENGTH;
  }

  private boolean validText(JsonNode... candidates) {
    for (JsonNode candidate : candidates) {
      if (candidate != null
          && candidate.isTextual()
          && !candidate.asText().isBlank()
          && candidate.asText().length() <= MAX_VALUE_LENGTH) {
        return true;
      }
    }
    return false;
  }

  private String context() {
    StringBuilder result = new StringBuilder("[system-added context]");
    append(result, "database", "Mentioned databases:");
    append(result, "table", "Mentioned tables:");
    append(result, "setting", "Mentioned settings:");
    return result.toString();
  }

  private void append(StringBuilder result, String kind, String heading) {
    List<JsonNode> mentions = active.get(kind);
    if (mentions == null) {
      return;
    }
    result.append('\n').append(heading);
    for (JsonNode mention : mentions) {
      result.append("\n- ").append(mention.path("name").asText()).append(" (");
      if ("setting".equals(kind)) {
        result.append("type: ").append(mention.path("type").asText());
      } else {
        result.append("engine: ").append(mention.path("engine").asText());
        if ("database".equals(kind)
            && mention.hasNonNull("comment")
            && !mention.path("comment").asText().isBlank()) {
          result.append(", comment: ").append(mention.path("comment").asText());
        }
      }
      result.append(')');
    }
  }
}
