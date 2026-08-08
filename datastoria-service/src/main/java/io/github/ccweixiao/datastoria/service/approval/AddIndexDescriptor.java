package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class AddIndexDescriptor extends AbstractTableDdlDescriptor {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOCAL_ONLY;
  }

  @Override
  public String generatorKey() {
    return "add_index";
  }

  @Override
  protected void validateTableRules(JsonNode rules) {
    Set<String> supported = Set.of("minmax", "set", "bloom_filter", "tokenbf_v1", "ngrambf_v1");
    JsonNode values = rules.path("allowedIndexTypes");
    if (!values.isArray() || values.isEmpty()) {
      throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
          io.github.ccweixiao.datastoria.common.error.ApiErrorCode.DDL_RULE_VIOLATION);
    }
    for (JsonNode value : values) {
      if (!supported.contains(value.asText())) {
        throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
            io.github.ccweixiao.datastoria.common.error.ApiErrorCode.DDL_RULE_VIOLATION);
      }
    }
    int max = rules.path("maxGranularity").asInt(0);
    if (max < 1 || max > 8192) {
      throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
          io.github.ccweixiao.datastoria.common.error.ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String index = rawIdentifier(requiredText(intent, "index"));
    String column = rawIdentifier(requiredText(intent, "column"));
    requireExistingColumn(schema, column);
    String indexType = rawIdentifier(requiredText(intent, "indexType"));
    int granularity = intent.path("granularity").asInt(0);
    JsonNode rules = rules(definition);
    boolean allowedType = false;
    for (JsonNode value : rules.path("allowedIndexTypes")) {
      allowedType |= indexType.equals(value.asText());
    }
    int maxGranularity = rules.path("maxGranularity").asInt(0);
    if (!allowedType || granularity < 1 || granularity > maxGranularity) {
      throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
          io.github.ccweixiao.datastoria.common.error.ApiErrorCode.DDL_RULE_VIOLATION);
    }
    TableTargets targets = targets(intent, definition);
    String physicalTable = targets.physicalTables().get(0);
    String table = qualified(targets.database(), physicalTable);
    String addSql =
        "ALTER TABLE "
            + table
            + " ADD INDEX "
            + identifier(index)
            + " "
            + identifier(column)
            + " TYPE "
            + indexType
            + " GRANULARITY "
            + granularity;
    CompiledDdlStatement add =
        new CompiledDdlStatement(
            1,
            DdlOperationKind.ALTER_TABLE_ADD_INDEX,
            addSql,
            List.of(targets.database() + "." + physicalTable + ".index." + index),
            "MEDIUM",
            List.of(),
            "PRECONDITION");
    if (!intent.path("materialize").asBoolean(false)) {
      return new CompiledDdlPlan(
          List.of(add), List.of("indexTypeAndGranularityValidated", "localTableOnly"));
    }
    CompiledDdlStatement materialize =
        new CompiledDdlStatement(
            2,
            DdlOperationKind.ALTER_TABLE_MATERIALIZE_INDEX,
            "ALTER TABLE " + table + " MATERIALIZE INDEX " + identifier(index),
            add.objectRefs(),
            "HIGH",
            List.of("materializingIndexConsumesIo"),
            "PRECONDITION");
    return new CompiledDdlPlan(
        List.of(add, materialize), List.of("indexTypeAndGranularityValidated", "localTableOnly"));
  }

  private static JsonNode rules(ApprovalTypeDefinition definition) {
    try {
      return JSON.readTree(definition.generationRuleJson());
    } catch (Exception exception) {
      throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
          io.github.ccweixiao.datastoria.common.error.ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }
}
