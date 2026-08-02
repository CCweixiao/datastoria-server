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
                new CreateStandardTableDescriptor(),
                new AddColumnDescriptor(),
                new ModifyColumnDescriptor(),
                new DropColumnDescriptor(),
                new AddIndexDescriptor()));
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
              "orderBy":["event_time","event_id"], "shardingKey":"event_id"
            }
            """,
            DdlSchemaSnapshot.EMPTY);

    assertThat(plan.statements()).hasSize(2);
    assertThat(plan.statements().get(0).sql())
        .contains("`analytics`.`events_local`", "ReplicatedMergeTree")
        .doesNotContain("events_all");
    assertThat(plan.statements().get(1).sql())
        .contains("`analytics`.`events_all`", "`analytics`.`events_local`")
        .contains("Distributed(`production`");
    assertThat(plan.statements()).extracting(CompiledDdlStatement::ordinal).containsExactly(1, 2);
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
        "add_index".equals(generator)
            ? "{\"allowedIndexTypes\":[\"minmax\",\"bloom_filter\"],\"maxGranularity\":8192}"
            : "{}",
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
}
