package io.github.ccweixiao.datastoria.service.approval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

@Component
public class CreateStandardTableDescriptor extends AbstractTableDdlDescriptor {

  private static final Set<String> DEFAULT_KINDS =
      Set.of("DEFAULT", "MATERIALIZED", "ALIAS", "EPHEMERAL");
  private static final Set<String> CODECS =
      Set.of(
          "NONE",
          "LZ4",
          "LZ4HC",
          "ZSTD",
          "DELTA",
          "DOUBLEDELTA",
          "GORILLA",
          "T64",
          "FPC",
          "GCD",
          "RLE");
  private static final Pattern CODEC_ITEM =
      Pattern.compile("([A-Za-z][A-Za-z0-9_]*)(?:\\(\\s*[0-9]+(?:\\s*,\\s*[0-9]+)*\\s*\\))?");
  private static final Set<String> SKIPPING_INDEX_TYPES =
      Set.of("minmax", "set", "bloom_filter", "tokenbf_v1", "ngrambf_v1");

  @Override
  protected TableTargetPolicy targetPolicy() {
    return TableTargetPolicy.LOGICAL_PAIR_LOCAL_FIRST;
  }

  @Override
  public String generatorKey() {
    return "create_local_distributed_table";
  }

  @Override
  protected void validateTableRules(JsonNode rules) {
    requireRule(rules, "requireCluster");
  }

  @Override
  public CompiledDdlPlan compile(
      JsonNode intent, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema) {
    TableTargets targets = targets(intent, definition);
    if (targets.explicitPhysical()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    String database = targets.database();
    String localName = targets.physicalTables().get(0);
    String distributedName = targets.physicalTables().get(1);
    String cluster = rawIdentifier(requiredText(intent, "cluster"));
    JsonNode columns = intent.path("columns");
    if (!columns.isArray() || columns.isEmpty()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
    }
    List<String> columnDefinitions = new ArrayList<>();
    List<String> columnNames = new ArrayList<>();
    List<String> indexDefinitions = new ArrayList<>();
    Set<String> indexNames = new HashSet<>();
    columns.forEach(
        column -> {
          String name = rawIdentifier(requiredText(column, "name"));
          columnNames.add(name);
          columnDefinitions.add(columnDefinition(name, column));
          appendIndexes(name, column, indexDefinitions, indexNames);
        });
    columnDefinitions.addAll(indexDefinitions);
    List<String> orderBy = orderByIdentifiers(intent, "orderBy");
    String partitionBy = optionalPartitionExpression(intent, "partitionBy", columnNames);
    String shardingKeyRaw = optionalText(intent, "shardingKey");
    if (shardingKeyRaw == null) {
      // shardingKey is agent-derived; default to the first sort column when the agent omits it.
      shardingKeyRaw = orderBy.get(0).substring(1, orderBy.get(0).length() - 1);
    }
    String shardingKey = rawIdentifier(shardingKeyRaw);
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
            + ")"
            + (partitionBy == null ? "" : "\nPARTITION BY " + partitionBy);
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
        indexDefinitions.isEmpty()
            ? List.of("createLocalAndDistributedPair", "logicalTableTargetsExpanded")
            : List.of(
                "createLocalAndDistributedPair",
                "logicalTableTargetsExpanded",
                "skippingIndexesValidated"));
  }

  private static void appendIndexes(
      String column, JsonNode definition, List<String> indexDefinitions, Set<String> indexNames) {
    if (definition.has("index")) {
      appendIndex(column, definition.path("index"), indexDefinitions, indexNames);
    }
    JsonNode indexes = definition.path("indexes");
    if (!indexes.isMissingNode() && !indexes.isArray()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
    }
    indexes.forEach(index -> appendIndex(column, index, indexDefinitions, indexNames));
  }

  private static void appendIndex(
      String column, JsonNode index, List<String> definitions, Set<String> names) {
    if (!index.isObject()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
    }
    String name = rawIdentifier(requiredText(index, "name"));
    String type = requiredText(index, "type").toLowerCase(Locale.ROOT);
    int granularity = index.path("granularity").asInt(0);
    if (!names.add(name)
        || !SKIPPING_INDEX_TYPES.contains(type)
        || granularity < 1
        || granularity > 8192) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    StringBuilder typeDefinition = new StringBuilder(type);
    JsonNode arguments = index.path("arguments");
    if (!arguments.isMissingNode()) {
      if (!arguments.isArray() || arguments.size() > 4) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
      }
      List<String> values = new ArrayList<>();
      arguments.forEach(
          argument -> {
            if (!argument.isNumber()
                || argument.decimalValue().signum() < 0
                || argument.decimalValue().compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE))
                    > 0) {
              throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
            }
            values.add(argument.decimalValue().stripTrailingZeros().toPlainString());
          });
      validateIndexArguments(type, values);
      if (!values.isEmpty()) {
        typeDefinition.append("(").append(String.join(", ", values)).append(")");
      }
    }
    definitions.add(
        "INDEX "
            + identifier(name)
            + " "
            + identifier(column)
            + " TYPE "
            + typeDefinition
            + " GRANULARITY "
            + granularity);
  }

  private static void validateIndexArguments(String type, List<String> arguments) {
    boolean valid =
        switch (type) {
          case "minmax" -> arguments.isEmpty();
          case "set", "bloom_filter" -> arguments.size() <= 1;
          case "tokenbf_v1" -> arguments.size() == 3;
          case "ngrambf_v1" -> arguments.size() == 4;
          default -> false;
        };
    if (valid && "bloom_filter".equals(type) && !arguments.isEmpty()) {
      java.math.BigDecimal probability = new java.math.BigDecimal(arguments.get(0));
      valid = probability.signum() > 0 && probability.compareTo(java.math.BigDecimal.ONE) <= 0;
    }
    if (!valid) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  private static String columnDefinition(String name, JsonNode column) {
    StringBuilder definition =
        new StringBuilder(identifier(name)).append(" ").append(columnType(column, "type"));
    String defaultKind =
        optionalText(column, "defaultKind", "default_kind", "defaultType", "default_type");
    String defaultExpression = optionalText(column, "defaultExpr", "default_expr");
    if (defaultKind == null && defaultExpression != null) defaultKind = "DEFAULT";
    if (defaultKind != null) {
      defaultKind = defaultKind.toUpperCase(Locale.ROOT);
      if (!DEFAULT_KINDS.contains(defaultKind)) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
      }
      if (defaultExpression == null && !"EPHEMERAL".equals(defaultKind)) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_INTENT_INVALID);
      }
      definition.append(" ").append(defaultKind);
      if (defaultExpression != null) {
        definition.append(" ").append(sqlExpression(defaultExpression));
      }
    }
    String comment = optionalText(column, "comment");
    if (comment != null) definition.append(" COMMENT ").append(sqlStringLiteral(comment));
    String codec = optionalText(column, "codec", "codecExpr", "codec_expr");
    if (codec != null) definition.append(" CODEC(").append(codecExpression(codec)).append(")");
    String ttl = optionalText(column, "ttl", "ttlExpr", "ttl_expr");
    if (ttl != null) definition.append(" TTL ").append(sqlExpression(ttl));
    return definition.toString();
  }

  private static String codecExpression(String value) {
    String expression = value.trim();
    if (expression.regionMatches(true, 0, "CODEC(", 0, 6) && expression.endsWith(")")) {
      expression = expression.substring(6, expression.length() - 1).trim();
    }
    String[] items = expression.split(",(?=\\s*[A-Za-z])");
    for (String item : items) {
      var matcher = CODEC_ITEM.matcher(item.trim());
      if (!matcher.matches() || !CODECS.contains(matcher.group(1).toUpperCase(Locale.ROOT))) {
        throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
      }
    }
    return String.join(", ", java.util.Arrays.stream(items).map(String::trim).toList());
  }
}
