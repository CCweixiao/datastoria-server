package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class RenameTableDescriptor extends AbstractTableDdlDescriptor {
  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOGICAL_PAIR_DISTRIBUTED_FIRST;
  }

  @Override
  public String generatorKey() {
    return "rename_table";
  }

  @Override
  protected void validateTableRules(JsonNode rules) {
    requireRule(rules, "requireCluster");
    requireRule(rules, "manualExecutionOnly");
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    TableTargets sources = targets(intent, definition);
    String newLogicalTable = rawIdentifier(requiredText(intent, "newTable"));
    com.fasterxml.jackson.databind.node.ObjectNode targetIntent =
        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    targetIntent.put("database", sources.database());
    targetIntent.put("table", newLogicalTable);
    TableTargets targets = targets(targetIntent, definition);
    if (sources.physicalTables().size() != targets.physicalTables().size()) {
      throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
          io.github.ccweixiao.datastoria.common.error.ApiErrorCode.DDL_RULE_VIOLATION);
    }
    String cluster = rawIdentifier(requiredText(intent, "cluster"));
    List<CompiledDdlStatement> statements = new java.util.ArrayList<>();
    for (int index = 0; index < sources.physicalTables().size(); index++) {
      String source = sources.physicalTables().get(index);
      String target = targets.physicalTables().get(index);
      String sql =
          "RENAME TABLE "
              + qualified(sources.database(), source)
              + " TO "
              + qualified(targets.database(), target)
              + " ON CLUSTER "
              + identifier(cluster);
      statements.add(
          new CompiledDdlStatement(
              index + 1,
              DdlOperationKind.RENAME_TABLE,
              sql,
              List.of(sources.database() + "." + source, targets.database() + "." + target),
              "HIGH",
              List.of("renameMayBreakDependentQueries"),
              "PRECONDITION"));
    }
    return new CompiledDdlPlan(
        statements,
        List.of(
            "requireSourceTable",
            "requireMissingTargetTable",
            "requireCluster",
            "distributedBeforeLocal"));
  }
}
