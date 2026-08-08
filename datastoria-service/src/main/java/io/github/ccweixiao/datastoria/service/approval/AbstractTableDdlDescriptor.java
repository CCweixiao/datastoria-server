package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

/** Shared logical-to-physical table expansion for every table-scoped DDL descriptor. */
abstract class AbstractTableDdlDescriptor extends AbstractDdlDescriptor {
  private static final ObjectMapper JSON = new ObjectMapper();

  protected abstract TableTargetPolicy targetPolicy();

  @Override
  public final void validateRules(JsonNode rules) {
    super.validateRules(rules);
    String localSuffix = rules.path("localSuffix").asText("_local");
    String distributedSuffix = rules.path("distributedSuffix").asText("_all");
    if (!localSuffix.matches("_[A-Za-z0-9_]+")
        || !distributedSuffix.matches("_[A-Za-z0-9_]+")
        || localSuffix.equals(distributedSuffix)) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    validateTableRules(rules);
  }

  protected void validateTableRules(JsonNode rules) {}

  protected static void requireProtectedColumnRules(JsonNode rules) {
    forbidRule(rules, "allowPromptOverride");
    JsonNode values = rules.path("protectKeys");
    if (!values.isArray()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    Set<String> keys = new java.util.HashSet<>();
    values.forEach(value -> keys.add(value.asText()));
    if (!keys.containsAll(Set.of("sorting_key", "primary_key", "partition_key", "sampling_key"))) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  protected final TableTargets targets(JsonNode intent, ApprovalTypeDefinition definition) {
    String database = rawIdentifier(requiredText(intent, "database"));
    String tableValue = optionalText(intent, "table");
    if (tableValue == null) tableValue = optionalText(intent, "tableName");
    if (tableValue == null) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
    }
    String table = rawIdentifier(tableValue);
    Rules rules = rules(definition);
    if (table.endsWith(rules.localSuffix()) || table.endsWith(rules.distributedSuffix())) {
      return new TableTargets(database, table, List.of(table), true);
    }
    List<String> physical =
        switch (targetPolicy()) {
          case LOCAL_ONLY -> List.of(table + rules.localSuffix());
          case LOGICAL_PAIR_LOCAL_FIRST -> List.of(
              table + rules.localSuffix(), table + rules.distributedSuffix());
          case LOGICAL_PAIR_DISTRIBUTED_FIRST -> List.of(
              table + rules.distributedSuffix(), table + rules.localSuffix());
        };
    return new TableTargets(database, table, physical, false);
  }

  protected static String qualified(String database, String table) {
    return identifier(database) + "." + identifier(table);
  }

  private static Rules rules(ApprovalTypeDefinition definition) {
    try {
      JsonNode rules = JSON.readTree(definition.generationRuleJson());
      return new Rules(
          suffix(rules, "localSuffix", "_local"), suffix(rules, "distributedSuffix", "_all"));
    } catch (Exception exception) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  private static String suffix(JsonNode rules, String field, String fallback) {
    String suffix = rules.path(field).asText(fallback);
    if (!suffix.matches("_[A-Za-z0-9_]+")) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    return suffix;
  }

  protected record TableTargets(
      String database, String logicalTable, List<String> physicalTables, boolean explicitPhysical) {
    protected TableTargets {
      physicalTables = List.copyOf(physicalTables);
    }
  }

  private record Rules(String localSuffix, String distributedSuffix) {}
}
