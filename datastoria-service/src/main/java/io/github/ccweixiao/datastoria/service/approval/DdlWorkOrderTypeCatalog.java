package io.github.ccweixiao.datastoria.service.approval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  private final List<DdlWorkOrderTypeSpecification> specifications;
  private final Map<String, DdlWorkOrderTypeDescriptor> descriptors;

  public DdlWorkOrderTypeCatalog(
      ApprovalRepository repository,
      ObjectMapper mapper,
      DdlWorkOrderTypeSpecificationRegistry specificationRegistry,
      List<DdlWorkOrderTypeDescriptor> descriptors) {
    this.repository = repository;
    this.mapper = mapper;
    this.specifications = specificationRegistry.all();
    this.descriptors =
        descriptors.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    DdlWorkOrderTypeDescriptor::generatorKey, Function.identity()));
    for (DdlWorkOrderTypeSpecification specification : specifications) {
      descriptor(specification.generatorKey()).validateRules(specification.defaultRules());
    }
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
      descriptor(current.generatorKey()).validateRules(rules);
      String normalizedRules = mapper.writeValueAsString(rules);
      String names =
          mapper.writeValueAsString(
              Map.of("en", command.nameEn().trim(), "zh-CN", command.nameZhCn().trim()));
      String descriptions =
          mapper.writeValueAsString(
              Map.of(
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

  private DdlWorkOrderTypeDescriptor descriptor(String generatorKey) {
    DdlWorkOrderTypeDescriptor descriptor = descriptors.get(generatorKey);
    if (descriptor == null) {
      throw PlainTextException.badRequest(ApiErrorCode.APPROVAL_WORK_ORDER_TYPE_UNSUPPORTED);
    }
    return descriptor;
  }

  private void ensureInitialDefinitions(String tenantId) {
    specifications.forEach(specification -> create(tenantId, specification));
  }

  private void create(String tenantId, DdlWorkOrderTypeSpecification specification) {
    Instant now = Instant.now();
    try {
      String names =
          mapper.writeValueAsString(
              Map.of("en", specification.nameEn(), "zh-CN", specification.nameZhCn()));
      String descriptions =
          mapper.writeValueAsString(
              Map.of(
                  "en", specification.descriptionEn(),
                  "zh-CN", specification.descriptionZhCn()));
      String operations = mapper.writeValueAsString(specification.allowedOperationKinds());
      String normalizedRules = mapper.writeValueAsString(specification.defaultRules());
      String checksum =
          sha256(
              specification.typeKey()
                  + "\n"
                  + specification.generatorKey()
                  + "\n"
                  + operations
                  + "\n"
                  + normalizedRules);
      repository.createTypeIfAbsent(
          new ApprovalTypeDefinition(
              Ulid.next(),
              tenantId,
              specification.typeKey(),
              "CLICKHOUSE_DDL",
              names,
              descriptions,
              specification.generatorKey(),
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

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
