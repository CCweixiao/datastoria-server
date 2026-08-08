package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class TruncateTableDescriptor extends AbstractTableDdlDescriptor {
  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOCAL_ONLY;
  }

  @Override
  public String generatorKey() {
    return "truncate_table";
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
    String table = targets.physicalTables().get(0);
    String cluster = rawIdentifier(requiredText(intent, "cluster"));
    String sql =
        "TRUNCATE TABLE "
            + qualified(targets.database(), table)
            + " ON CLUSTER "
            + identifier(cluster);
    return new CompiledDdlPlan(
        List.of(
            new CompiledDdlStatement(
                1,
                DdlOperationKind.TRUNCATE_TABLE,
                sql,
                List.of(targets.database() + "." + table),
                "CRITICAL",
                List.of("truncateTablePermanentlyRemovesAllRows"),
                "PRECONDITION")),
        List.of("requireExistingTable", "requireCluster", "manualExecutionOnly", "localTableOnly"));
  }
}
