package io.github.ccweixiao.datastoria.service.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

class DdlPlanCompilerTest {

  private final ObjectMapper mapper = new ObjectMapper();
  private DdlPlanCompiler compiler;

  @BeforeEach
  void setUp() {
    compiler =
        new DdlPlanCompiler(
            List.of(
                new CreateDatabaseDescriptor(),
                new CreateStandardTableDescriptor(),
                new AddColumnDescriptor(),
                new ModifyColumnDescriptor(),
                new DropColumnDescriptor(),
                new AddIndexDescriptor(),
                new RenameTableDescriptor(),
                new DropTableDescriptor(),
                new TruncateTableDescriptor(),
                new DropIndexDescriptor()));
  }

  @Test
  void standardTableAlwaysProducesLocalThenDistributedTables() throws Exception {
    CompiledDdlPlan plan =
        compile(
            "create_local_distributed_table",
            """
            {
              "database":"analytics", "table":"events", "cluster":"production",
              "columns":[{"name":"event_id","type":"UInt64"},{"name":"event_time","type":"DateTime"}],
              "orderBy":["event_time","event_id"], "partitionBy":"toYYYYMM(event_time)",
              "shardingKey":"event_id"
            }
            """,
            DdlSchemaSnapshot.EMPTY);

    assertThat(plan.statements()).hasSize(2);
    assertThat(plan.statements().get(0).sql())
        .contains(
            "`analytics`.`events_local`",
            "ReplicatedMergeTree",
            "PARTITION BY toYYYYMM(`event_time`)")
        .doesNotContain("events_all");
    assertThat(plan.statements().get(1).sql())
        .contains("`analytics`.`events_all`", "`analytics`.`events_local`")
        .contains("Distributed(`production`");
    assertThat(plan.statements()).extracting(CompiledDdlStatement::ordinal).containsExactly(1, 2);
  }

  @Test
  void standardTableAcceptsTableNameAliasStringOrderByAndDefaultsShardingKey() throws Exception {
    // Mirrors what the agent actually emits before it loads the ruleGuide: 'tableName' instead of
    // 'table', orderBy as a SQL fragment instead of an array, and no shardingKey. The descriptor
    // accepts the unambiguous variants and defaults shardingKey to the first sort column.
    CompiledDdlPlan plan =
        compile(
            "create_local_distributed_table",
            """
            {
              "database":"test", "tableName":"users", "cluster":"test_cluster",
              "columns":[{"name":"user_id","type":"UInt64"},{"name":"status","type":"String"}],
              "orderBy":"(status, user_id)"
            }
            """,
            DdlSchemaSnapshot.EMPTY);

    assertThat(plan.statements()).hasSize(2);
    assertThat(plan.statements().get(0).sql())
        .contains("`test`.`users_local`")
        .contains("ORDER BY (`status`, `user_id`)");
    // shardingKey defaulted to orderBy[0] = status
    assertThat(plan.statements().get(1).sql())
        .contains("`test`.`users_all`")
        .contains("Distributed(`test_cluster`, `test`, `users_local`, `status`)");
  }

  @Test
  void standardTableCompilesOfficialClickHouseColumnClauses() throws Exception {
    CompiledDdlPlan plan =
        compile(
            "create_local_distributed_table",
            """
            {
              "database":"analytics", "table":"events", "cluster":"production",
              "columns":[
                {"name":"id","type":"UInt64","default_expr":"generateUUIDv7()","comment":"业务'主键","codec":"Delta, ZSTD(3)"},
                {"name":"created_at","type":"DateTime","defaultKind":"DEFAULT","defaultExpr":"now()","ttl":"created_at + INTERVAL 90 DAY"},
                {"name":"event_date","type":"Date","default_type":"MATERIALIZED","default_expr":"toDate(created_at)"},
                {"name":"display_id","type":"String","defaultKind":"ALIAS","defaultExpr":"toString(id)"},
                {"name":"input_only","type":"Enum8('web'=1, 'app'=2)","defaultKind":"EPHEMERAL"}
              ],
              "orderBy":["created_at","id"], "shardingKey":"id"
            }
            """,
            DdlSchemaSnapshot.EMPTY);

    assertThat(plan.statements().get(0).sql())
        .contains("`id` UInt64 DEFAULT generateUUIDv7() COMMENT '业务\\'主键' CODEC(Delta, ZSTD(3))")
        .contains("`created_at` DateTime DEFAULT now() TTL created_at + INTERVAL 90 DAY")
        .contains("`event_date` Date MATERIALIZED toDate(created_at)")
        .contains("`display_id` String ALIAS toString(id)")
        .contains("`input_only` Enum8('web'=1, 'app'=2) EPHEMERAL");
  }

  @Test
  void standardTableRejectsUnsafeColumnExpressionsAndUnknownCodecs() {
    assertRuleViolation(
        "create_local_distributed_table",
        """
        {"database":"analytics","table":"events","cluster":"production",
         "columns":[{"name":"id","type":"UInt64","defaultExpr":"1; DROP TABLE users"}],
         "orderBy":["id"],"shardingKey":"id"}
        """,
        DdlSchemaSnapshot.EMPTY);
    assertRuleViolation(
        "create_local_distributed_table",
        """
        {"database":"analytics","table":"events","cluster":"production",
         "columns":[{"name":"id","type":"UInt64","codec":"UnknownCodec(1)"}],
         "orderBy":["id"],"shardingKey":"id"}
        """,
        DdlSchemaSnapshot.EMPTY);
  }

  @Test
  void standardTableCompilesPerColumnSkippingIndexes() throws Exception {
    CompiledDdlPlan plan =
        compile(
            "create_local_distributed_table",
            """
            {"database":"analytics","table":"events","cluster":"production",
             "columns":[
               {"name":"id","type":"UInt64","index":{"name":"idx_id","type":"minmax","granularity":1}},
               {"name":"message","type":"String","indexes":[
                 {"name":"idx_message_tokens","type":"tokenbf_v1","arguments":[10240,3,0],"granularity":4},
                 {"name":"idx_message_bloom","type":"bloom_filter","arguments":[0.01],"granularity":2}
               ]}
             ],"orderBy":["id"],"shardingKey":"id"}
            """,
            DdlSchemaSnapshot.EMPTY);

    assertThat(plan.statements().get(0).sql())
        .contains("INDEX `idx_id` `id` TYPE minmax GRANULARITY 1")
        .contains("INDEX `idx_message_tokens` `message` TYPE tokenbf_v1(10240, 3, 0) GRANULARITY 4")
        .contains("INDEX `idx_message_bloom` `message` TYPE bloom_filter(0.01) GRANULARITY 2");
    assertThat(plan.ruleSummaries()).contains("skippingIndexesValidated");
  }

  @Test
  void standardTableRejectsDuplicateOrUnsupportedInlineIndexes() {
    assertRuleViolation(
        "create_local_distributed_table",
        """
        {"database":"analytics","table":"events","cluster":"production",
         "columns":[{"name":"id","type":"UInt64","indexes":[
           {"name":"idx_id","type":"minmax","granularity":1},
           {"name":"idx_id","type":"set","granularity":1}
         ]}],"orderBy":["id"],"shardingKey":"id"}
        """,
        DdlSchemaSnapshot.EMPTY);
    assertRuleViolation(
        "create_local_distributed_table",
        """
        {"database":"analytics","table":"events","cluster":"production",
         "columns":[{"name":"id","type":"UInt64","index":{"name":"idx_id","type":"vector_similarity","granularity":1}}],
         "orderBy":["id"],"shardingKey":"id"}
        """,
        DdlSchemaSnapshot.EMPTY);
  }

  @Test
  void createDatabaseEmitsSingleOnClusterAtomicStatement() throws Exception {
    CompiledDdlPlan plan =
        compile(
            "create_database",
            """
            {"database":"analytics","cluster":"production"}
            """,
            DdlSchemaSnapshot.EMPTY);

    assertThat(plan.statements()).hasSize(1);
    assertThat(plan.statements().get(0).sql())
        .contains("CREATE DATABASE `analytics`")
        .contains("ON CLUSTER `production`")
        .contains("ENGINE = Atomic");
    assertThat(plan.statements().get(0).operationKind())
        .isEqualTo(DdlOperationKind.CREATE_DATABASE);
    assertThat(plan.statements().get(0).objectRefs()).containsExactly("analytics");
  }

  @Test
  void tableLifecycleDescriptorsEmitConstrainedOnClusterStatements() throws Exception {
    CompiledDdlPlan rename =
        compile(
            "rename_table",
            """
        {"database":"analytics","table":"events","newTable":"events_v2","cluster":"production"}
        """,
            DdlSchemaSnapshot.EMPTY);
    assertThat(rename.statements()).hasSize(2);
    assertThat(rename.statements().get(0).sql())
        .isEqualTo(
            "RENAME TABLE `analytics`.`events_all` TO `analytics`.`events_v2_all` ON CLUSTER `production`");
    assertThat(rename.statements().get(1).sql())
        .contains("`analytics`.`events_local`", "`analytics`.`events_v2_local`");
    assertThat(
            compile(
                    "drop_table",
                    """
        {"database":"analytics","table":"events","cluster":"production"}
        """,
                    DdlSchemaSnapshot.EMPTY)
                .statements()
                .get(0)
                .riskLevel())
        .isEqualTo("CRITICAL");
    assertThat(
            compile(
                    "truncate_table",
                    """
        {"database":"analytics","table":"events","cluster":"production"}
        """,
                    DdlSchemaSnapshot.EMPTY)
                .statements()
                .get(0)
                .operationKind())
        .isEqualTo(DdlOperationKind.TRUNCATE_TABLE);
    assertThat(
            compile(
                    "drop_index",
                    """
        {"database":"analytics","table":"events","index":"idx_status"}
        """,
                    DdlSchemaSnapshot.EMPTY)
                .statements()
                .get(0)
                .sql())
        .isEqualTo("ALTER TABLE `analytics`.`events_local` DROP INDEX `idx_status`");
  }

  @Test
  void standardTableRejectsUserControlledSuffixAndInvalidShardingKey() {
    assertRuleViolation(
        "create_local_distributed_table",
        """
        {"database":"analytics","table":"events_local","cluster":"production",
         "columns":[{"name":"id","type":"UInt64"}],"orderBy":["id"],"shardingKey":"id"}
        """,
        DdlSchemaSnapshot.EMPTY);
    assertRuleViolation(
        "create_local_distributed_table",
        """
        {"database":"analytics","table":"events","cluster":"production",
         "columns":[{"name":"id","type":"UInt64"}],"orderBy":["id"],"shardingKey":"missing"}
        """,
        DdlSchemaSnapshot.EMPTY);
  }

  @Test
  void columnDescriptorsCompileOnlyTheirDeclaredOperation() throws Exception {
    DdlSchemaSnapshot schema = new DdlSchemaSnapshot(Set.of("id", "payload"), Set.of("id"));

    assertThat(
            compile(
                    "add_column",
                    """
                    {"database":"analytics","table":"events","column":"source","type":"LowCardinality(String)"}
                    """,
                    schema)
                .statements()
                .get(0)
                .operationKind())
        .isEqualTo(DdlOperationKind.ALTER_TABLE_ADD_COLUMN);
    assertThat(
            compile(
                    "modify_column",
                    """
                    {"database":"analytics","table":"events","column":"payload","type":"Nullable(String)"}
                    """,
                    schema)
                .statements()
                .get(0)
                .operationKind())
        .isEqualTo(DdlOperationKind.ALTER_TABLE_MODIFY_COLUMN);
    assertThat(
            compile(
                    "drop_column",
                    """
                    {"database":"analytics","table":"events","column":"payload"}
                    """,
                    schema)
                .statements()
                .get(0)
                .riskLevel())
        .isEqualTo("CRITICAL");
  }

  @Test
  void logicalTableTargetsExpandByOperationPolicy() throws Exception {
    DdlSchemaSnapshot schema = new DdlSchemaSnapshot(Set.of("id", "payload"), Set.of("id"));
    CompiledDdlPlan add =
        compile(
            "add_column",
            """
        {"database":"analytics","table":"events","column":"source","type":"String"}
        """,
            schema);
    assertThat(add.statements()).hasSize(2);
    assertThat(add.statements())
        .extracting(CompiledDdlStatement::sql)
        .containsExactly(
            "ALTER TABLE `analytics`.`events_local` ADD COLUMN `source` String",
            "ALTER TABLE `analytics`.`events_all` ADD COLUMN `source` String");

    CompiledDdlPlan drop =
        compile(
            "drop_column",
            """
        {"database":"analytics","table":"events","column":"payload"}
        """,
            schema);
    assertThat(drop.statements())
        .extracting(CompiledDdlStatement::sql)
        .containsExactly(
            "ALTER TABLE `analytics`.`events_all` DROP COLUMN `payload`",
            "ALTER TABLE `analytics`.`events_local` DROP COLUMN `payload`");

    CompiledDdlPlan index =
        compile(
            "add_index",
            """
        {"database":"analytics","table":"events","index":"idx_payload","column":"payload",
         "indexType":"minmax","granularity":4}
        """,
            schema);
    assertThat(index.statements()).hasSize(1);
    assertThat(index.statements().get(0).sql()).contains("`events_local`");
  }

  @Test
  void modifyAndDropCannotTargetKeyColumns() {
    DdlSchemaSnapshot schema = new DdlSchemaSnapshot(Set.of("id"), Set.of("id"));
    assertRuleViolation(
        "modify_column",
        """
        {"database":"analytics","table":"events","column":"id","type":"String"}
        """,
        schema);
    assertRuleViolation(
        "drop_column",
        """
        {"database":"analytics","table":"events","column":"id"}
        """,
        schema);
  }

  @Test
  void addIndexOptionallyAddsMaterializationInStableOrder() throws Exception {
    CompiledDdlPlan plan =
        compile(
            "add_index",
            """
            {"database":"analytics","table":"events","index":"payload_bf",
             "column":"payload","indexType":"bloom_filter","granularity":4,"materialize":true}
            """,
            new DdlSchemaSnapshot(Set.of("payload"), Set.of()));

    assertThat(plan.statements())
        .extracting(CompiledDdlStatement::operationKind)
        .containsExactly(
            DdlOperationKind.ALTER_TABLE_ADD_INDEX, DdlOperationKind.ALTER_TABLE_MATERIALIZE_INDEX);
  }

  @Test
  void addIndexRejectsTypeOutsideConfiguredAllowList() {
    assertRuleViolation(
        "add_index",
        """
        {"database":"analytics","table":"events","index":"payload_set",
         "column":"payload","indexType":"set","granularity":4}
        """,
        new DdlSchemaSnapshot(Set.of("payload"), Set.of()));
  }

  @Test
  void identifiersAndTypesCannotInjectAdditionalStatements() {
    assertRuleViolation(
        "add_column",
        """
        {"database":"analytics","table":"events; DROP TABLE users","column":"x","type":"String"}
        """,
        DdlSchemaSnapshot.EMPTY);
    assertRuleViolation(
        "add_column",
        """
        {"database":"analytics","table":"events","column":"x","type":"String; DROP TABLE users"}
        """,
        DdlSchemaSnapshot.EMPTY);
  }

  private CompiledDdlPlan compile(String generator, String json, DdlSchemaSnapshot schema)
      throws Exception {
    JsonNode intent = mapper.readTree(json);
    return compiler.compile(intent, definition(generator), schema);
  }

  private void assertRuleViolation(String generator, String json, DdlSchemaSnapshot schema) {
    assertThatThrownBy(() -> compile(generator, json, schema))
        .isInstanceOfSatisfying(
            PlainTextException.class,
            error -> assertThat(error.code()).isEqualTo(ApiErrorCode.DDL_RULE_VIOLATION));
  }

  private ApprovalTypeDefinition definition(String generator) {
    Instant now = Instant.now();
    return new ApprovalTypeDefinition(
        "type-id",
        "tenant",
        "TYPE_KEY",
        "CLICKHOUSE_DDL",
        "{}",
        "{}",
        generator,
        "[]",
        rules(generator),
        null,
        "{}",
        "ENABLED",
        1,
        "checksum",
        "system",
        "system",
        "system",
        now,
        now,
        now);
  }

  private static String rules(String generator) {
    return switch (generator) {
      case "create_local_distributed_table" -> "{\"requireCluster\":true}";
      case "create_database" -> "{\"requireCluster\":true}";
      case "add_column" -> "{\"requireMissingColumn\":true}";
      case "modify_column",
          "drop_column" -> "{\"protectKeys\":[\"sorting_key\",\"primary_key\",\"partition_key\",\"sampling_key\"],\"allowPromptOverride\":false}";
      case "add_index" -> "{\"allowedIndexTypes\":[\"minmax\",\"bloom_filter\"],\"maxGranularity\":8192}";
      case "rename_table",
          "drop_table",
          "truncate_table" -> "{\"requireCluster\":true,\"manualExecutionOnly\":true}";
      case "drop_index" -> "{\"requireExistingIndex\":true}";
      default -> "{}";
    };
  }
}
