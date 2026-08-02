package io.github.ccweixiao.datastoria.service.approval;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

abstract class AbstractDdlDescriptor implements DdlWorkOrderTypeDescriptor {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
  private static final Pattern COLUMN_TYPE =
      Pattern.compile("[A-Za-z0-9_(), ']+(?:\\s+CODEC\\([A-Za-z0-9_(), ']+\\))?");

  protected static String requiredText(JsonNode intent, String field) {
    String value = intent.path(field).asText("").trim();
    if (value.isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
    return value;
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
    return value;
  }

  protected static String qualifiedTable(JsonNode intent) {
    return identifier(intent, "database") + "." + identifier(intent, "table");
  }

  protected static List<String> identifierArray(JsonNode intent, String field) {
    JsonNode values = intent.path(field);
    if (!values.isArray() || values.isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(identifier(value.asText())));
    return result;
  }

  protected static void requireExistingColumn(DdlSchemaSnapshot schema, String column) {
    if (schema.columns().isEmpty() || !schema.columns().contains(column.toLowerCase(Locale.ROOT))) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  protected static void requireMutableColumn(DdlSchemaSnapshot schema, String column) {
    requireExistingColumn(schema, column);
    if (schema.protectedColumns().contains(column.toLowerCase(Locale.ROOT))) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }
}
