package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.ConflictException;

@Component
public class AddColumnDescriptor extends AbstractTableDdlDescriptor {

  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOGICAL_PAIR_LOCAL_FIRST;
  }

  @Override
  public String generatorKey() {
    return "add_column";
  }

  @Override
  protected void validateTableRules(JsonNode rules) {
    requireRule(rules, "requireMissingColumn");
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String column = rawIdentifier(requiredText(intent, "column"));
    String type = columnType(intent, "type");
    TableTargets targets = targets(intent, definition);
    if (schema.columns().isEmpty()) {
      throw new ConflictException(ApiErrorCode.DDL_TARGET_NOT_FOUND);
    }
    if (schema.columns().contains(column.toLowerCase(Locale.ROOT))) {
      throw new ConflictException(ApiErrorCode.DDL_TARGET_ALREADY_EXISTS);
    }
    List<CompiledDdlStatement> statements = new java.util.ArrayList<>();
    for (int index = 0; index < targets.physicalTables().size(); index++) {
      String table = targets.physicalTables().get(index);
      String sql =
          "ALTER TABLE "
              + qualified(targets.database(), table)
              + " ADD COLUMN "
              + identifier(column)
              + " "
              + type;
      statements.add(
          new CompiledDdlStatement(
              index + 1,
              DdlOperationKind.ALTER_TABLE_ADD_COLUMN,
              sql,
              List.of(targets.database() + "." + table + "." + column),
              "LOW",
              List.of(),
              "PRECONDITION"));
    }
    return new CompiledDdlPlan(
        statements, List.of("columnMustNotExist", "logicalTableTargetsExpanded"));
  }
}
