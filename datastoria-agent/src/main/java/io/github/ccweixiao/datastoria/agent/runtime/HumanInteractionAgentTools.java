package io.github.ccweixiao.datastoria.agent.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.ToolSuspendException;

/**
 * Server-side HITL tools. They suspend AgentScope; the browser only resolves durable actions.
 *
 * <p>{@link #askUserQuestion} normalizes common provider-compact shapes (the browser performs the
 * same normalization for rendering) and then validates the resulting structure, so a malformed
 * question fails the tool call with a precise, correctable message instead of rendering a broken
 * form in the browser.
 */
public final class HumanInteractionAgentTools {

  private static final Set<String> QUESTION_KEYS = Set.of("header", "description", "options");
  private static final Set<String> OPTION_KEYS =
      Set.of("id", "label", "description", "input", "choices");
  private static final Set<String> INPUT_MODES = Set.of("none", "text", "select");
  private static final int MAX_OPTIONS = 6;
  private static final int MAX_CHOICES = 20;

  @Tool(
      name = "ask_user_question",
      description = "Ask exactly one structured follow-up question and wait for the user's answer.",
      readOnly = true)
  public String askUserQuestion(
      @ToolParam(
              name = "questions",
              description =
                  "Exactly one question object: { \"header\": short title, \"options\": [ "
                      + "{ \"id\": \"o1\", \"label\": \"short choice\", \"input\": \"none\" } ] }. "
                      + "Set \"input\" to \"text\" when the user must type a free-form answer, "
                      + "\"select\" with a \"choices\" string array for a fixed set, or \"none\" for "
                      + "a single click. An optional \"description\" (string) is allowed on the "
                      + "question and on each option. Emit only these fields; do not invent "
                      + "alternatives such as { label, description }.")
          List<Map<String, Object>> questions) {
    if (questions == null || questions.size() != 1) {
      throw new IllegalArgumentException("ask_user_question requires exactly one question");
    }
    Map<String, Object> canonical = canonicalize(questions.get(0));
    validateQuestion(canonical);
    throw new ToolSuspendException("Waiting for user response");
  }

  /**
   * Canonicalizes one question payload. The strict shape passes through; the compact shapes some
   * OpenAI-compatible providers emit ({@code {question, options: string[]}}, {@code {question,
   * choices: [{label, description}]}}, {@code {question, options: [{label, description}]}}) are
   * mapped onto it. Returns null when the payload matches none of the known shapes.
   */
  private static Map<String, Object> canonicalize(Map<String, Object> question) {
    if (question == null) {
      return null;
    }
    if (question.containsKey("header")
        && question.get("header") instanceof String
        && question.get("options") instanceof List) {
      return question;
    }
    if (!(question.get("question") instanceof String header) || header.isBlank()) {
      return null;
    }
    List<Map<String, Object>> options = new ArrayList<>();
    if (everyIs(question.get("options"), value -> value instanceof String s && !s.isBlank())) {
      for (Object option : (List<?>) question.get("options")) {
        options.add(plainOption((String) option));
      }
    } else if (everyIs(
        question.get("options"),
        value -> value instanceof Map<?, ?> map && nonBlankString(map.get("label")))) {
      for (Object option : (List<?>) question.get("options")) {
        Map<?, ?> labeled = (Map<?, ?>) option;
        Map<String, Object> mapped = plainOption(String.valueOf(labeled.get("label")));
        if (nonBlankString(labeled.get("description"))) {
          mapped.put("description", labeled.get("description"));
        }
        options.add(mapped);
      }
    } else if (everyIs(
        question.get("choices"),
        value -> value instanceof Map<?, ?> map && nonBlankString(map.get("label")))) {
      for (Object choice : (List<?>) question.get("choices")) {
        Map<?, ?> labeled = (Map<?, ?>) choice;
        Map<String, Object> mapped = plainOption(String.valueOf(labeled.get("label")));
        if (nonBlankString(labeled.get("description"))) {
          mapped.put("description", labeled.get("description"));
        }
        options.add(mapped);
      }
    } else {
      return null;
    }
    Map<String, Object> canonical = new LinkedHashMap<>();
    canonical.put("header", header);
    canonical.put("options", options);
    return canonical;
  }

  private static Map<String, Object> plainOption(String label) {
    Map<String, Object> option = new LinkedHashMap<>();
    option.put("label", label);
    return option; // id/input are filled in during validation below
  }

  private interface ValuePredicate {
    boolean accepts(Object value);
  }

  private static boolean everyIs(Object rawList, ValuePredicate predicate) {
    if (!(rawList instanceof List<?> list) || list.isEmpty()) {
      return false;
    }
    for (Object value : list) {
      if (!predicate.accepts(value)) {
        return false;
      }
    }
    return true;
  }

  private static boolean nonBlankString(Object value) {
    return value instanceof String text && !text.isBlank();
  }

  private static void validateQuestion(Map<String, Object> question) {
    if (question == null) {
      throw invalid(
          "unrecognized question shape; expected { header, options: [{ id, label, input }] } or a"
              + " compact { question, options|choices } form");
    }
    rejectUnknownKeys(question.keySet(), QUESTION_KEYS, "question");
    String header = string(question.get("header"), "header");
    requireLength(header, 1, 200, "header");
    optionalText(question.get("description"), "description", 500);
    Object rawOptions = question.get("options");
    if (!(rawOptions instanceof List<?> options) || options.isEmpty()) {
      throw invalid("question.options must be a non-empty array");
    }
    if (options.size() > MAX_OPTIONS) {
      throw invalid("question.options must have at most " + MAX_OPTIONS + " entries");
    }
    Set<String> seenIds = new HashSet<>();
    for (int index = 0; index < options.size(); index++) {
      validateOption(options.get(index), index, seenIds);
    }
  }

  private static void validateOption(Object rawOption, int index, Set<String> seenIds) {
    if (!(rawOption instanceof Map<?, ?> option)) {
      throw invalid("each option must be an object");
    }
    Set<String> keys = new HashSet<>();
    option.keySet().forEach(key -> keys.add(String.valueOf(key)));
    // Canonicalized compact options may omit id/input; fill them in deterministically.
    Map<String, Object> effective = new LinkedHashMap<>();
    Object id = option.get("id");
    effective.put("id", id != null ? id : "option-" + (index + 1));
    effective.put("label", option.get("label"));
    if (option.containsKey("description")) {
      effective.put("description", option.get("description"));
    }
    effective.put("input", option.containsKey("input") ? option.get("input") : "none");
    if (option.containsKey("choices")) {
      effective.put("choices", option.get("choices"));
    }
    rejectUnknownKeys(keys, OPTION_KEYS, "option");
    String optionId = string(effective.get("id"), "option.id");
    requireLength(optionId, 1, 100, "option.id");
    if (!seenIds.add(optionId)) {
      throw invalid("option.id values must be unique; duplicate: " + optionId);
    }
    requireLength(string(effective.get("label"), "option.label"), 1, 200, "option.label");
    optionalText(effective.get("description"), "option.description", 300);
    String input = string(effective.get("input"), "option.input");
    if (!INPUT_MODES.contains(input)) {
      throw invalid(
          "option.input must be one of " + INPUT_MODES + " (default \"none\"), got: " + input);
    }
    if ("select".equals(input)) {
      Object rawChoices = effective.get("choices");
      if (!(rawChoices instanceof List<?> choices) || choices.isEmpty()) {
        throw invalid("option.input \"select\" requires a non-empty choices string array");
      }
      if (choices.size() > MAX_CHOICES) {
        throw invalid("option.choices must have at most " + MAX_CHOICES + " entries");
      }
      for (Object choice : choices) {
        if (!(choice instanceof String value) || value.isBlank()) {
          throw invalid("option.choices entries must be non-empty strings");
        }
      }
    }
  }

  private static void rejectUnknownKeys(Set<String> actual, Set<String> allowed, String scope) {
    for (String key : actual) {
      if (!allowed.contains(key)) {
        throw invalid(
            "unknown "
                + scope
                + " field \""
                + key
                + "\"; allowed fields are "
                + allowed
                + ". Fix the structure and call ask_user_question again.");
      }
    }
  }

  private static String string(Object value, String field) {
    if (!(value instanceof String text) || text.isBlank()) {
      throw invalid(field + " must be a non-blank string");
    }
    return text;
  }

  /** Optional string field: any string (blank allowed) up to {@code max} characters. */
  private static void optionalText(Object value, String field, int max) {
    if (value == null) {
      return;
    }
    if (!(value instanceof String text)) {
      throw invalid(field + " must be a string");
    }
    if (text.length() > max) {
      throw invalid(field + " length must be at most " + max + " characters");
    }
  }

  private static void requireLength(String value, int min, int max, String field) {
    if (value.length() < min || value.length() > max) {
      throw invalid(field + " length must be between " + min + " and " + max + " characters");
    }
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException("Invalid ask_user_question payload: " + message);
  }
}
