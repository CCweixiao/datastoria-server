package io.github.ccweixiao.datastoria.service.approval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

@Component
public class CreateStandardTableDescriptor extends AbstractDdlDescriptor {

  @Override
  public String generatorKey() {
    return "create_local_distributed_table";
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    String database = rawIdentifier(requiredText(intent, "database"));
    String logicalTable = rawIdentifier(requiredText(intent, "table"));
    if (logicalTable.endsWith("_local") || logicalTable.endsWith("_all")) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    String cluster = rawIdentifier(requiredText(intent, "cluster"));
    String localName = logicalTable + "_local";
    String distributedName = logicalTable + "_all";
    JsonNode columns = intent.path("columns");
    if (!columns.isArray() || columns.isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
    List<String> columnDefinitions = new ArrayList<>();
    List<String> columnNames = new ArrayList<>();
    columns.forEach(
        column -> {
          String name = rawIdentifier(requiredText(column, "name"));
          columnNames.add(name);
          columnDefinitions.add(identifier(name) + " " + columnType(column, "type"));
        });
    List<String> orderBy = identifierArray(intent, "orderBy");
    String shardingKey = rawIdentifier(requiredText(intent, "shardingKey"));
    if (!columnNames.contains(shardingKey)) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    for (String orderColumn : orderBy) {
      String plain = orderColumn.substring(1, orderColumn.length() - 1);
      if (!columnNames.contains(plain)) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
      }
    }
    String localSql =
        "CREATE TABLE "
            + identifier(database)
            + "."
            + identifier(localName)
            + " ON CLUSTER "
            + identifier(cluster)
            + " (\n  "
            + String.join(",\n  ", columnDefinitions)
            + "\n) ENGINE = ReplicatedMergeTree\nORDER BY ("
            + String.join(", ", orderBy)
            + ")";
    String distributedSql =
        "CREATE TABLE "
            + identifier(database)
            + "."
            + identifier(distributedName)
            + " ON CLUSTER "
            + identifier(cluster)
            + " AS "
            + identifier(database)
            + "."
            + identifier(localName)
            + "\nENGINE = Distributed("
            + identifier(cluster)
            + ", "
            + identifier(database)
            + ", "
            + identifier(localName)
            + ", "
            + identifier(shardingKey)
            + ")";
    return new CompiledDdlPlan(
        List.of(
            new CompiledDdlStatement(
                1,
                DdlOperationKind.CREATE_TABLE,
                localSql,
                List.of(database + "." + localName),
                "MEDIUM",
                List.of(),
                "PRECONDITION"),
            new CompiledDdlStatement(
                2,
                DdlOperationKind.CREATE_TABLE,
                distributedSql,
                List.of(database + "." + distributedName, database + "." + localName),
                "MEDIUM",
                List.of(),
                "PRECONDITION")),
        List.of("createLocalAndDistributedPair", "localSuffix=_local", "distributedSuffix=_all"));
  }
}
