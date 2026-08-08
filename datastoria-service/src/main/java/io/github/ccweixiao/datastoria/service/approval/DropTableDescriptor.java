package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class DropTableDescriptor extends AbstractTableDdlDescriptor {
  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOGICAL_PAIR_DISTRIBUTED_FIRST;
  }

  @Override
  public String generatorKey() {
    return "drop_table";
  }

  @Override
  protected void validateTableRules(JsonNode rules) {
    requireRule(rules, "requireCluster");
    requireRule(rules, "manualExecutionOnly");
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    TableTargets targets = targets(intent, definition);
    String cluster = rawIdentifier(requiredText(intent, "cluster"));
    List<CompiledDdlStatement> statements = new java.util.ArrayList<>();
    for (int index = 0; index < targets.physicalTables().size(); index++) {
      String table = targets.physicalTables().get(index);
      String sql =
          "DROP TABLE "
              + qualified(targets.database(), table)
              + " ON CLUSTER "
              + identifier(cluster);
      statements.add(
          new CompiledDdlStatement(
              index + 1,
              DdlOperationKind.DROP_TABLE,
              sql,
              List.of(targets.database() + "." + table),
              "CRITICAL",
              List.of("dropTablePermanentlyRemovesData"),
              "PRECONDITION"));
    }
    return new CompiledDdlPlan(
        statements,
        List.of(
            "requireExistingTable",
            "requireCluster",
            "manualExecutionOnly",
            "distributedBeforeLocal"));
  }
}
