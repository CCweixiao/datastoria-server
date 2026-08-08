package io.github.ccweixiao.datastoria.service.approval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.ConflictException;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

abstract class AbstractDdlDescriptor implements DdlWorkOrderTypeDescriptor {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern COLUMN_TYPE = Pattern.compile("[A-Za-z0-9_(), '\\[\\].=+\\-]+");
  private static final Pattern PARTITION_FUNCTION =
      Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\(([A-Za-z_][A-Za-z0-9_]*)\\)");
  private static final Set<String> PARTITION_FUNCTIONS =
      Set.of("toDate", "toYYYYMM", "toYYYYMMDD", "toStartOfHour", "toStartOfDay", "toStartOfMonth");

  @Override
  public void validateRules(JsonNode rules) {
    if (rules == null || !rules.isObject()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  protected static void requireRule(JsonNode rules, String field) {
    if (!rules.path(field).asBoolean(false)) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  protected static void forbidRule(JsonNode rules, String field) {
    if (rules.path(field).asBoolean(true)) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  protected static String requiredText(JsonNode intent, String field) {
    String value = intent.path(field).asText("").trim();
    if (value.isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
    }
    return value;
  }

  /**
   * Reads an optional text field, returning null when absent/blank (caller decides the default).
   */
  protected static String optionalText(JsonNode intent, String field) {
    String value = intent.path(field).asText("").trim();
    return value.isEmpty() ? null : value;
  }

  protected static String identifier(JsonNode intent, String field) {
    return identifier(requiredText(intent, field));
  }

  protected static String identifier(String value) {
    if (!IDENTIFIER.matcher(value).matches()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    return "`" + value + "`";
  }

  protected static String rawIdentifier(String value) {
    if (!IDENTIFIER.matcher(value).matches()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    return value;
  }

  protected static String columnType(JsonNode intent, String field) {
    String value = requiredText(intent, field);
    if (!COLUMN_TYPE.matcher(value).matches()
        || value.contains("--")
        || value.contains("/*")
        || value.contains(";")) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    return sqlExpression(value);
  }

  protected static String optionalText(JsonNode intent, String... fields) {
    for (String field : fields) {
      String value = optionalText(intent, field);
      if (value != null) return value;
    }
    return null;
  }

  /**
   * Validates a single ClickHouse expression fragment without attempting to reimplement its
   * grammar.
   */
  protected static String sqlExpression(String value) {
    String expression = value == null ? "" : value.trim();
    if (expression.isEmpty()
        || expression.length() > 2048
        || expression.contains(";")
        || expression.contains("--")
        || expression.contains("/*")
        || expression.contains("*/")) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    int parentheses = 0;
    int brackets = 0;
    boolean quoted = false;
    for (int index = 0; index < expression.length(); index++) {
      char current = expression.charAt(index);
      if (Character.isISOControl(current)) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
      }
      if (quoted) {
        if (current == '\\') {
          index++;
        } else if (current == '\''
            && index + 1 < expression.length()
            && expression.charAt(index + 1) == '\'') {
          index++;
        } else if (current == '\'') {
          quoted = false;
        }
        continue;
      }
      switch (current) {
        case '\'' -> quoted = true;
        case '(' -> parentheses++;
        case ')' -> parentheses--;
        case '[' -> brackets++;
        case ']' -> brackets--;
        default -> {}
      }
      if (parentheses < 0 || brackets < 0) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
      }
    }
    if (quoted || parentheses != 0 || brackets != 0) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    return expression;
  }

  protected static String sqlStringLiteral(String value) {
    if (value == null || value.length() > 1024 || value.chars().anyMatch(Character::isISOControl)) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
  }

  protected static String qualifiedTable(JsonNode intent) {
    return identifier(intent, "database") + "." + identifier(intent, "table");
  }

  protected static List<String> identifierArray(JsonNode intent, String field) {
    JsonNode values = intent.path(field);
    if (!values.isArray() || values.isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
    }
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(identifier(value.asText())));
    return result;
  }

  /**
   * Reads an order-by column list that may arrive as a string array (the canonical contract) or as
   * a SQL-style fragment like {@code "(a, b, c)"} / {@code "a, b, c"} emitted by some agents. Each
   * token is validated as an identifier and returned backtick-quoted.
   */
  protected static List<String> orderByIdentifiers(JsonNode intent, String field) {
    JsonNode values = intent.path(field);
    List<String> raw = new ArrayList<>();
    if (values.isArray()) {
      values.forEach(value -> raw.add(value.asText("").trim()));
    } else if (values.isTextual()) {
      String text = values.asText("").trim();
      if (text.startsWith("(")) {
        text = text.substring(1);
      }
      if (text.endsWith(")")) {
        text = text.substring(0, text.length() - 1);
      }
      for (String part : text.split(",")) {
        String trimmed = part.trim();
        if (!trimmed.isEmpty()) {
          raw.add(trimmed);
        }
      }
    }
    if (raw.isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
    }
    List<String> result = new ArrayList<>();
    for (String token : raw) {
      result.add(identifier(token));
    }
    return result;
  }

  protected static String optionalPartitionExpression(
      JsonNode intent, String field, List<String> columnNames) {
    String expression = optionalText(intent, field);
    if (expression == null) return null;
    if (IDENTIFIER.matcher(expression).matches()) {
      if (!columnNames.contains(expression)) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
      }
      return identifier(expression);
    }
    var matcher = PARTITION_FUNCTION.matcher(expression);
    if (!matcher.matches()
        || !PARTITION_FUNCTIONS.contains(matcher.group(1))
        || !columnNames.contains(matcher.group(2))) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    return matcher.group(1) + "(" + identifier(matcher.group(2)) + ")";
  }

  protected static void requireExistingColumn(DdlSchemaSnapshot schema, String column) {
    if (schema.columns().isEmpty() || !schema.columns().contains(column.toLowerCase(Locale.ROOT))) {
      throw new ConflictException(ApiErrorCode.DDL_TARGET_NOT_FOUND);
    }
  }

  protected static void requireMutableColumn(DdlSchemaSnapshot schema, String column) {
    requireExistingColumn(schema, column);
    if (schema.protectedColumns().contains(column.toLowerCase(Locale.ROOT))) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }
}
