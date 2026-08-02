package io.github.ccweixiao.datastoria.service.approval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTypeUpdateRequest;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.ConflictException;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.dao.repository.ApprovalRepository;

@Service
public class DdlWorkOrderTypeCatalog {

  private static final String SYSTEM_ACTOR = "system";
  private final ApprovalRepository repository;
  private final ObjectMapper mapper;

  public DdlWorkOrderTypeCatalog(ApprovalRepository repository, ObjectMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public List<ApprovalTypeDefinition> listEnabled(String tenantId, String connectionId) {
    ensureInitialDefinitions(tenantId);
    return repository.findEnabledTypes(tenantId, connectionId);
  }

  public ApprovalTypeDefinition requireEnabled(String tenantId, String typeKey) {
    ensureInitialDefinitions(tenantId);
    return repository
        .findEnabledType(tenantId, typeKey)
        .orElseThrow(
            () -> PlainTextException.badRequest(ApiErrorCode.APPROVAL_WORK_ORDER_TYPE_UNSUPPORTED));
  }

  public List<ApprovalTypeDefinition> listAll(String tenantId) {
    ensureInitialDefinitions(tenantId);
    return repository.findTypes(tenantId);
  }

  public ApprovalTypeDefinition update(
      String tenantId, String typeKey, ApprovalTypeUpdateRequest command, String actorUserId) {
    ensureInitialDefinitions(tenantId);
    if (command == null
        || command.revision() < 1
        || isBlank(command.nameEn())
        || isBlank(command.nameZhCn())
        || isBlank(command.descriptionEn())
        || isBlank(command.descriptionZhCn())
        || isBlank(command.generationRuleJson())) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
    ApprovalTypeDefinition current =
        repository
            .findType(tenantId, typeKey)
            .orElseThrow(
                () ->
                    PlainTextException.badRequest(
                        ApiErrorCode.APPROVAL_WORK_ORDER_TYPE_UNSUPPORTED));
    try {
      JsonNode rules = mapper.readTree(command.generationRuleJson());
      validateSafeRules(current.generatorKey(), rules);
      String normalizedRules = mapper.writeValueAsString(rules);
      String names =
          mapper.writeValueAsString(
              java.util.Map.of("en", command.nameEn().trim(), "zh-CN", command.nameZhCn().trim()));
      String descriptions =
          mapper.writeValueAsString(
              java.util.Map.of(
                  "en", command.descriptionEn().trim(), "zh-CN", command.descriptionZhCn().trim()));
      String checksum =
          sha256(
              current.typeKey()
                  + "\n"
                  + current.generatorKey()
                  + "\n"
                  + current.allowedOperationKindsJson()
                  + "\n"
                  + normalizedRules);
      if (!repository.updateType(
          tenantId,
          typeKey,
          command.revision(),
          names,
          descriptions,
          normalizedRules,
          command.enabled() ? "ENABLED" : "DISABLED",
          checksum,
          actorUserId)) {
        throw new ConflictException(ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
      }
      return repository.findType(tenantId, typeKey).orElseThrow();
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  private static void validateSafeRules(String generatorKey, JsonNode rules) {
    if (!rules.isObject()) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
    boolean valid =
        switch (generatorKey) {
          case "create_local_distributed_table" -> "_local"
                  .equals(rules.path("localSuffix").asText())
              && "_all".equals(rules.path("distributedSuffix").asText())
              && rules.path("requireCluster").asBoolean(false);
          case "add_column" -> rules.path("requireMissingColumn").asBoolean(false);
          case "modify_column", "drop_column" -> !rules.path("allowPromptOverride").asBoolean(true)
              && containsAllProtectedKeys(rules.path("protectKeys"));
          case "add_index" -> validIndexRules(rules);
          default -> false;
        };
    if (!valid) {
      throw PlainTextException.badRequest(ApiErrorCode.DDL_RULE_VIOLATION);
    }
  }

  private static boolean containsAllProtectedKeys(JsonNode values) {
    if (!values.isArray()) return false;
    Set<String> keys = new java.util.HashSet<>();
    values.forEach(value -> keys.add(value.asText()));
    return keys.containsAll(Set.of("sorting_key", "primary_key", "partition_key", "sampling_key"));
  }

  private static boolean validIndexRules(JsonNode rules) {
    Set<String> supported = Set.of("minmax", "set", "bloom_filter", "tokenbf_v1", "ngrambf_v1");
    JsonNode values = rules.path("allowedIndexTypes");
    if (!values.isArray() || values.isEmpty()) return false;
    for (JsonNode value : values) {
      if (!supported.contains(value.asText())) return false;
    }
    int max = rules.path("maxGranularity").asInt(0);
    return max >= 1 && max <= 8192;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void ensureInitialDefinitions(String tenantId) {
    create(
        tenantId,
        "CLICKHOUSE_CREATE_TABLE",
        "Standard table",
        "标准建表",
        "Create a required local table and its distributed table as one ordered plan.",
        "按固定顺序创建本地表及其分布式表。",
        "create_local_distributed_table",
        List.of("CREATE_TABLE"),
        """
        {"localSuffix":"_local","distributedSuffix":"_all","requireCluster":true}
        """);
    create(
        tenantId,
        "CLICKHOUSE_ADD_COLUMN",
        "Add column",
        "增加字段",
        "Add a column after confirming that it does not already exist.",
        "确认字段不存在后增加字段。",
        "add_column",
        List.of("ALTER_TABLE_ADD_COLUMN"),
        """
        {"requireMissingColumn":true}
        """);
    create(
        tenantId,
        "CLICKHOUSE_MODIFY_COLUMN",
        "Modify column",
        "修改字段",
        "Modify a column while protecting sorting, primary, partition, and sampling keys.",
        "修改字段，同时保护排序键、主键、分区键和采样键。",
        "modify_column",
        List.of("ALTER_TABLE_MODIFY_COLUMN"),
        protectedColumnRule());
    create(
        tenantId,
        "CLICKHOUSE_DROP_COLUMN",
        "Drop column",
        "删除字段",
        "Drop a non-key column after mandatory dependency checks.",
        "通过强制依赖检查后删除非关键字段。",
        "drop_column",
        List.of("ALTER_TABLE_DROP_COLUMN"),
        protectedColumnRule());
    create(
        tenantId,
        "CLICKHOUSE_ADD_INDEX",
        "Add skipping index",
        "增加索引",
        "Add a skipping index and optionally materialize it in the same ordered plan.",
        "增加跳数索引，并可在同一有序计划中物化索引。",
        "add_index",
        List.of("ALTER_TABLE_ADD_INDEX", "ALTER_TABLE_MATERIALIZE_INDEX"),
        """
        {"allowedIndexTypes":["minmax","set","bloom_filter","tokenbf_v1","ngrambf_v1"],"maxGranularity":8192}
        """);
  }

  private void create(
      String tenantId,
      String typeKey,
      String nameEn,
      String nameZh,
      String descriptionEn,
      String descriptionZh,
      String generatorKey,
      List<String> operationKinds,
      String generationRuleJson) {
    Instant now = Instant.now();
    try {
      String names = mapper.writeValueAsString(java.util.Map.of("en", nameEn, "zh-CN", nameZh));
      String descriptions =
          mapper.writeValueAsString(java.util.Map.of("en", descriptionEn, "zh-CN", descriptionZh));
      String operations = mapper.writeValueAsString(operationKinds);
      String normalizedRules = mapper.writeValueAsString(mapper.readTree(generationRuleJson));
      String checksum =
          sha256(typeKey + "\n" + generatorKey + "\n" + operations + "\n" + normalizedRules);
      repository.createTypeIfAbsent(
          new ApprovalTypeDefinition(
              Ulid.next(),
              tenantId,
              typeKey,
              "CLICKHOUSE_DDL",
              names,
              descriptions,
              generatorKey,
              operations,
              normalizedRules,
              null,
              "{\"executionMode\":\"MANUAL_TRIGGER\"}",
              "ENABLED",
              1,
              checksum,
              SYSTEM_ACTOR,
              SYSTEM_ACTOR,
              SYSTEM_ACTOR,
              now,
              now,
              now));
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid built-in approval type definition", exception);
    }
  }

  private static String protectedColumnRule() {
    return """
        {"protectKeys":["sorting_key","primary_key","partition_key","sampling_key"],"allowPromptOverride":false}
        """;
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
