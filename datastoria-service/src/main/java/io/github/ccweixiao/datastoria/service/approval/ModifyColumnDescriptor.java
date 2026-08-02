package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;

@Component
public class ModifyColumnDescriptor extends AbstractDdlDescriptor {

  @Override
  public String generatorKey() {
    return "modify_column";
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String column = rawIdentifier(requiredText(intent, "column"));
    requireMutableColumn(schema, column);
    String sql =
        "ALTER TABLE "
            + qualifiedTable(intent)
            + " MODIFY COLUMN "
            + identifier(column)
            + " "
            + columnType(intent, "type");
    return new CompiledDdlPlan(
        List.of(
            new CompiledDdlStatement(
                1,
                DdlOperationKind.ALTER_TABLE_MODIFY_COLUMN,
                sql,
                List.of(
                    requiredText(intent, "database")
                        + "."
                        + requiredText(intent, "table")
                        + "."
                        + column),
                "HIGH",
                List.of("typeChangeMayRewriteData"),
                "PRECONDITION")),
        List.of("protectSortingPrimaryPartitionSamplingKeys"));
  }
}
