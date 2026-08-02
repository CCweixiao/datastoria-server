package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class AddIndexDescriptor extends AbstractDdlDescriptor {

  @Override
  public String generatorKey() {
    return "add_index";
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String index = rawIdentifier(requiredText(intent, "index"));
    String column = rawIdentifier(requiredText(intent, "column"));
    requireExistingColumn(schema, column);
    String indexType = rawIdentifier(requiredText(intent, "indexType"));
    int granularity = intent.path("granularity").asInt(0);
    if (granularity < 1 || granularity > 8192) {
      throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
          io.github.ccweixiao.datastoria.common.error.ApiErrorCode.DDL_RULE_VIOLATION);
    }
    String table = qualifiedTable(intent);
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
            List.of(
                requiredText(intent, "database")
                    + "."
                    + requiredText(intent, "table")
                    + "."
                    + index),
            "MEDIUM",
            List.of(),
            "PRECONDITION");
    if (!intent.path("materialize").asBoolean(false)) {
      return new CompiledDdlPlan(List.of(add), List.of("indexTypeAndGranularityValidated"));
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
        List.of(add, materialize), List.of("indexTypeAndGranularityValidated"));
  }
}
