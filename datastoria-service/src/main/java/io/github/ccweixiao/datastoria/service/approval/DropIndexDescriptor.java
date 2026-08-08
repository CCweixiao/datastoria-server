package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class DropIndexDescriptor extends AbstractTableDdlDescriptor {
  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOCAL_ONLY;
  }

  @Override
  public String generatorKey() {
    return "drop_index";
  }

  @Override
  protected void validateTableRules(JsonNode rules) {
    requireRule(rules, "requireExistingIndex");
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    TableTargets targets = targets(intent, definition);
    String database = targets.database();
    String table = targets.physicalTables().get(0);
    String index = rawIdentifier(requiredText(intent, "index"));
    String sql =
        "ALTER TABLE "
            + identifier(database)
            + "."
            + identifier(table)
            + " DROP INDEX "
            + identifier(index);
    return new CompiledDdlPlan(
        List.of(
            new CompiledDdlStatement(
                1,
                DdlOperationKind.ALTER_TABLE_DROP_INDEX,
                sql,
                List.of(database + "." + table + ".index." + index),
                "HIGH",
                List.of("droppingIndexMayDegradeQueryPerformance"),
                "PRECONDITION")),
        List.of("requireExistingTable", "requireExistingIndex"));
  }
}
