package io.github.ccweixiao.datastoria.common.dto.approval;

import java.util.List;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;

public record ApprovalWorkOrderTypeResponse(
    String typeKey,
    String nameI18nJson,
    String descriptionI18nJson,
    List<String> requiredIntentFields,
    List<IntentField> intentSchema,
    String ruleSummary,
    String ruleGuide,
    long definitionRevision) {

  /**
   * Declarative intent field (V2 intent_schema): tells the Agent what to pass for each type, so it
   * does not call {@code prepare} blind. {@code source} follows the V2 taxonomy — {@code
   * user-provided} (Agent cannot know, must ask), {@code agent-derived} (Agent can infer), {@code
   * schema-verified} (server checks), {@code mixed}.
   */
  public record IntentField(
      String name,
      String type,
      boolean required,
      String source,
      String questionEn,
      String questionZh) {}

  public ApprovalWorkOrderTypeResponse {
    requiredIntentFields = List.copyOf(requiredIntentFields);
    intentSchema = List.copyOf(intentSchema);
  }

  /**
   * Single source of truth for the work-order contract shared by the admin capabilities API and the
   * {@code list_approval_work_order_types} agent tool. Keeping the {@code generatorKey} switches
   * here guarantees the agent sees the exact field contract (names, types, required) plus a natural
   * language {@link #ruleGuide()} — the agent loads this before building intent, instead of
   * guessing field names like {@code tableName}.
   */
  public static ApprovalWorkOrderTypeResponse from(ApprovalTypeDefinition type) {
    return new ApprovalWorkOrderTypeResponse(
        type.typeKey(),
        type.nameI18nJson(),
        type.descriptionI18nJson(),
        requiredIntentFields(type.generatorKey()),
        intentSchema(type.generatorKey()),
        ruleSummary(type.generatorKey()),
        ruleGuide(type.generatorKey()),
        type.definitionRevision());
  }

  private static List<String> requiredIntentFields(String generatorKey) {
    return switch (generatorKey) {
      case "create_local_distributed_table" -> List.of(
          "database", "table", "cluster", "columns", "orderBy", "shardingKey");
      case "create_database" -> List.of("database", "cluster");
      case "add_column", "modify_column" -> List.of("database", "table", "column", "type");
      case "drop_column" -> List.of("database", "table", "column");
      case "add_index" -> List.of(
          "database", "table", "index", "column", "indexType", "granularity");
      case "rename_table" -> List.of("database", "table", "newTable", "cluster");
      case "drop_table", "truncate_table" -> List.of("database", "table", "cluster");
      case "drop_index" -> List.of("database", "table", "index");
      default -> List.of();
    };
  }

  private static List<IntentField> intentSchema(String generatorKey) {
    return switch (generatorKey) {
      case "create_local_distributed_table" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "Which database should contain the table?",
              "要在哪个数据库中建表？"),
          field(
              "table",
              "identifier",
              "user-provided",
              "What is the logical table name (without _local or _all)?",
              "逻辑表名是什么（不要带 _local 或 _all 后缀）？"),
          field(
              "cluster",
              "identifier",
              "user-provided",
              "Which ClickHouse cluster should receive the table?",
              "要在哪个 ClickHouse 集群创建表？"),
          field(
              "columns",
              "array",
              "mixed",
              "What columns are required, including each name, ClickHouse type, nullability, and default semantics?",
              "需要哪些字段？请给出字段名、ClickHouse 类型、是否可空及默认值语义。"),
          field(
              "orderBy",
              "array",
              "agent-derived",
              "Which columns should form ORDER BY, based on the most common filters and query order?",
              "结合最常见的过滤与查询顺序，哪些字段应作为 ORDER BY？"),
          field(
              "shardingKey",
              "identifier",
              "agent-derived",
              "Which high-cardinality, evenly distributed column should be the sharding key?",
              "哪个高基数且分布均匀的字段应作为分片键？"));
      case "create_database" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "What database name should be created?",
              "要创建的数据库名称是什么？"),
          field(
              "cluster",
              "identifier",
              "user-provided",
              "Which ClickHouse cluster should receive the database?",
              "要在哪个 ClickHouse 集群创建数据库？"));
      case "add_column", "modify_column" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "Which database contains the table?",
              "目标表位于哪个数据库？"),
          field(
              "table", "identifier", "user-provided", "Which table should be changed?", "要变更哪张表？"),
          field(
              "column",
              "identifier",
              "user-provided",
              "Which column should be added or modified?",
              "要新增或修改哪个字段？"),
          field(
              "type",
              "columnType",
              "user-provided",
              "What is the complete target ClickHouse column type?",
              "目标 ClickHouse 字段类型完整定义是什么？"));
      case "drop_column" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "Which database contains the table?",
              "目标表位于哪个数据库？"),
          field(
              "table", "identifier", "user-provided", "Which table should be changed?", "要变更哪张表？"),
          field(
              "column",
              "identifier",
              "user-provided",
              "Which column should be dropped, and has downstream usage been checked?",
              "要删除哪个字段，是否已确认下游没有依赖？"));
      case "add_index" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "Which database contains the table?",
              "目标表位于哪个数据库？"),
          field(
              "table",
              "identifier",
              "user-provided",
              "Which table should receive the skipping index?",
              "要给哪张表添加跳数索引？"),
          field(
              "index",
              "identifier",
              "user-provided",
              "What should the index be named?",
              "索引名称是什么？"),
          field(
              "column",
              "identifier",
              "user-provided",
              "Which column or supported expression should the index cover?",
              "索引覆盖哪个字段或受支持的表达式？"),
          field(
              "indexType",
              "identifier",
              "user-provided",
              "Which index type matches the query predicate and data distribution?",
              "结合查询谓词和数据分布，应选择哪种索引类型？"),
          field(
              "granularity",
              "number",
              "user-provided",
              "What index granularity should be used (1 to 8192)?",
              "索引 GRANULARITY 使用多少（1 到 8192）？"));
      case "rename_table" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "Which database contains the table?",
              "目标表位于哪个数据库？"),
          field(
              "table",
              "identifier",
              "user-provided",
              "Which existing table should be renamed?",
              "要重命名哪张已有表？"),
          field(
              "newTable",
              "identifier",
              "user-provided",
              "What should the new table name be?",
              "新的表名是什么？"),
          field(
              "cluster",
              "identifier",
              "user-provided",
              "Which ClickHouse cluster should receive the rename?",
              "要在哪个 ClickHouse 集群执行重命名？"));
      case "drop_table", "truncate_table" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "Which database contains the table?",
              "目标表位于哪个数据库？"),
          field(
              "table",
              "identifier",
              "user-provided",
              "Which table is the destructive operation targeting?",
              "破坏性操作针对哪张表？"),
          field(
              "cluster",
              "identifier",
              "user-provided",
              "Which ClickHouse cluster should receive the operation?",
              "要在哪个 ClickHouse 集群执行？"));
      case "drop_index" -> List.of(
          field(
              "database",
              "identifier",
              "user-provided",
              "Which database contains the table?",
              "目标表位于哪个数据库？"),
          field(
              "table",
              "identifier",
              "user-provided",
              "Which table contains the index?",
              "索引位于哪张表？"),
          field(
              "index",
              "identifier",
              "user-provided",
              "Which skipping index should be dropped?",
              "要删除哪个跳数索引？"));
      default -> List.of();
    };
  }

  private static String ruleSummary(String generatorKey) {
    return switch (generatorKey) {
      case "create_local_distributed_table" -> "CREATE_LOCAL_AND_DISTRIBUTED_PAIR";
      case "create_database" -> "CREATE_DATABASE";
      case "modify_column", "drop_column" -> "PROTECT_KEY_COLUMNS";
      case "add_column" -> "REQUIRE_MISSING_COLUMN";
      case "add_index" -> "VALIDATE_SKIPPING_INDEX";
      case "rename_table" -> "REQUIRE_SOURCE_AND_MISSING_TARGET";
      case "drop_table", "truncate_table" -> "DESTRUCTIVE_MANUAL_ONLY";
      case "drop_index" -> "REQUIRE_EXISTING_INDEX";
      default -> "SERVER_VALIDATED";
    };
  }

  /**
   * Natural-language contract the agent loads before building intent. Names the exact required
   * fields and the common deviations the server rejects, so the agent emits a conforming intent on
   * the first try instead of discovering the contract through validation errors.
   */
  private static String ruleGuide(String generatorKey) {
    return switch (generatorKey) {
      case "create_local_distributed_table" -> "Required intent fields: 'database', 'table' (the logical name; the server appends the "
          + "_local/_all suffixes, so do NOT add them and use 'table' not 'tableName'), "
          + "'cluster', 'columns' (array of {name, type}), 'orderBy' (a string ARRAY of column "
          + "names, e.g. [\"status\",\"user_id\"]), 'shardingKey' (one column name present in "
          + "columns). The server always emits a ReplicatedMergeTree local table plus a "
          + "Distributed table. Optional 'partitionBy' accepts a column or a supported date bucket "
          + "function such as toYYYYMM(created_at). Each column requires {name,type} and optionally "
          + "accepts defaultKind (DEFAULT, MATERIALIZED, ALIAS, EPHEMERAL), defaultExpr, comment, "
          + "codec, ttl, and index/indexes. A skipping index is {name,type,granularity} with optional "
          + "numeric arguments; supported types are minmax, set, bloom_filter, tokenbf_v1, and "
          + "ngrambf_v1. Snake_case aliases such as default_expr are also accepted. The table "
          + "engine remains server-controlled. The target database must already exist; if it does not, submit a "
          + "CLICKHOUSE_CREATE_DATABASE work order first.";
      case "create_database" -> "Required intent fields: 'database', 'cluster'. Creates the database on the cluster with "
          + "ENGINE = Atomic. Blocks if the database already exists; create-table work orders "
          + "require the target database to exist first.";
      case "add_column" -> "Required intent fields: 'database', 'table', 'column', 'type'. The column must not "
          + "already exist (the server verifies).";
      case "modify_column" -> "Required intent fields: 'database', 'table', 'column', 'type'. sorting_key, primary_key, "
          + "partition_key and sampling_key columns are protected and cannot be modified.";
      case "drop_column" -> "Required intent fields: 'database', 'table', 'column'. sorting_key, primary_key, "
          + "partition_key and sampling_key columns are protected and cannot be dropped.";
      case "add_index" -> "Required intent fields: 'database', 'table', 'index', 'column', 'indexType' (one of "
          + "minmax, set, bloom_filter, tokenbf_v1, ngrambf_v1), 'granularity' (integer "
          + "1..8192).";
      case "rename_table" -> "Required intent fields: 'database', 'table', 'newTable', 'cluster'. The source must exist and the target must not exist.";
      case "drop_table",
          "truncate_table" -> "Required intent fields: 'database', 'table', 'cluster'. This is destructive, requires explicit confirmation, and can only be executed manually by an administrator.";
      case "drop_index" -> "Required intent fields: 'database', 'table', 'index'. The table and skipping index must already exist.";
      default -> "";
    };
  }

  private static IntentField field(
      String name, String type, String source, String questionEn, String questionZh) {
    return new IntentField(name, type, true, source, questionEn, questionZh);
  }
}
