package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class DropColumnDescriptor extends AbstractDdlDescriptor {

  @Override
  public String generatorKey() {
    return "drop_column";
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String column = rawIdentifier(requiredText(intent, "column"));
    requireMutableColumn(schema, column);
    String sql = "ALTER TABLE " + qualifiedTable(intent) + " DROP COLUMN " + identifier(column);
    return new CompiledDdlPlan(
        List.of(
            new CompiledDdlStatement(
                1,
                DdlOperationKind.ALTER_TABLE_DROP_COLUMN,
                sql,
                List.of(
                    requiredText(intent, "database")
                        + "."
                        + requiredText(intent, "table")
                        + "."
                        + column),
                "CRITICAL",
                List.of("dropColumnPermanentlyRemovesData"),
                "PRECONDITION")),
        List.of("protectSortingPrimaryPartitionSamplingKeys"));
  }
}
