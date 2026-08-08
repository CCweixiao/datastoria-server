package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class ModifyColumnDescriptor extends AbstractTableDdlDescriptor {

  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOGICAL_PAIR_LOCAL_FIRST;
  }

  @Override
  public String generatorKey() {
    return "modify_column";
  }

  @Override
  protected void validateTableRules(JsonNode rules) {
    requireProtectedColumnRules(rules);
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String column = rawIdentifier(requiredText(intent, "column"));
    requireMutableColumn(schema, column);
    TableTargets targets = targets(intent, definition);
    String type = columnType(intent, "type");
    List<CompiledDdlStatement> statements = new java.util.ArrayList<>();
    for (int index = 0; index < targets.physicalTables().size(); index++) {
      String table = targets.physicalTables().get(index);
      statements.add(
          new CompiledDdlStatement(
              index + 1,
              DdlOperationKind.ALTER_TABLE_MODIFY_COLUMN,
              "ALTER TABLE "
                  + qualified(targets.database(), table)
                  + " MODIFY COLUMN "
                  + identifier(column)
                  + " "
                  + type,
              List.of(targets.database() + "." + table + "." + column),
              "HIGH",
              List.of("typeChangeMayRewriteData"),
              "PRECONDITION"));
    }
    return new CompiledDdlPlan(
        statements,
        List.of("protectSortingPrimaryPartitionSamplingKeys", "logicalTableTargetsExpanded"));
  }
}
