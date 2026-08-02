package io.github.ccweixiao.datastoria.service.approval;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

@Component
public class AddColumnDescriptor extends AbstractDdlDescriptor {

  @Override
  public String generatorKey() {
    return "add_column";
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String column = rawIdentifier(requiredText(intent, "column"));
    if (schema.columns().isEmpty() || schema.columns().contains(column.toLowerCase(Locale.ROOT))) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    String sql =
        "ALTER TABLE "
            + qualifiedTable(intent)
            + " ADD COLUMN "
            + identifier(column)
            + " "
            + columnType(intent, "type");
    return new CompiledDdlPlan(
        List.of(
            new CompiledDdlStatement(
                1,
                DdlOperationKind.ALTER_TABLE_ADD_COLUMN,
                sql,
                List.of(
                    requiredText(intent, "database")
                        + "."
                        + requiredText(intent, "table")
                        + "."
                        + column),
                "LOW",
                List.of(),
                "PRECONDITION")),
        List.of("columnMustNotExist"));
  }
}
