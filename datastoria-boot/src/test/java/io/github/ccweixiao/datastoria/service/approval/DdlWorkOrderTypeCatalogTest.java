package io.github.ccweixiao.datastoria.service.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTypeUpdateRequest;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.dao.repository.ApprovalRepository;

class DdlWorkOrderTypeCatalogTest {

  @Test
  void resourceManifestDefinesAndSeedsAllRegisteredTypes() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    DdlWorkOrderTypeCatalog catalog = catalog(repository);
    when(repository.findTypes("tenant")).thenReturn(List.of());

    catalog.listAll("tenant");

    verify(repository, times(10)).createTypeIfAbsent(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void administratorCannotDisableMandatoryKeyProtection() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    DdlWorkOrderTypeCatalog catalog = catalog(repository);
    when(repository.findType("tenant", "CLICKHOUSE_DROP_COLUMN"))
        .thenReturn(Optional.of(definition("drop_column", protectedRules())));

    assertThatThrownBy(
            () ->
                catalog.update(
                    "tenant",
                    "CLICKHOUSE_DROP_COLUMN",
                    command("{\"protectKeys\":[\"sorting_key\"],\"allowPromptOverride\":true}"),
                    "admin"))
        .isInstanceOfSatisfying(
            PlainTextException.class,
            error -> assertThat(error.code()).isEqualTo(ApiErrorCode.DDL_RULE_VIOLATION));
  }

  @Test
  void administratorCanNarrowIndexRulesAndDisableType() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    DdlWorkOrderTypeCatalog catalog = catalog(repository);
    ApprovalTypeDefinition current =
        definition(
            "add_index", "{\"allowedIndexTypes\":[\"minmax\",\"set\"],\"maxGranularity\":8192}");
    ApprovalTypeDefinition updated =
        new ApprovalTypeDefinition(
            current.id(),
            current.tenantId(),
            current.typeKey(),
            current.handlerKey(),
            current.nameI18nJson(),
            current.descriptionI18nJson(),
            current.generatorKey(),
            current.allowedOperationKindsJson(),
            "{\"allowedIndexTypes\":[\"minmax\"],\"maxGranularity\":64}",
            null,
            current.riskPolicyJson(),
            "DISABLED",
            2,
            "updated",
            "system",
            "admin",
            null,
            current.createdAt(),
            Instant.now(),
            null);
    when(repository.findType("tenant", "TYPE_KEY"))
        .thenReturn(Optional.of(current), Optional.of(updated));
    when(repository.updateType(
            eq("tenant"),
            eq("TYPE_KEY"),
            eq(1L),
            anyString(),
            anyString(),
            anyString(),
            eq("DISABLED"),
            anyString(),
            eq("admin")))
        .thenReturn(true);

    ApprovalTypeDefinition result =
        catalog.update(
            "tenant",
            "TYPE_KEY",
            command("{\"allowedIndexTypes\":[\"minmax\"],\"maxGranularity\":64}"),
            "admin");

    assertThat(result.status()).isEqualTo("DISABLED");
    verify(repository)
        .updateType(
            eq("tenant"),
            eq("TYPE_KEY"),
            eq(1L),
            anyString(),
            anyString(),
            anyString(),
            eq("DISABLED"),
            anyString(),
            eq("admin"));
  }

  private static ApprovalTypeUpdateRequest command(String rules) {
    return new ApprovalTypeUpdateRequest(1, "Name", "名称", "Description", "描述", rules, false);
  }

  private static DdlWorkOrderTypeCatalog catalog(ApprovalRepository repository) {
    ObjectMapper mapper = new ObjectMapper();
    return new DdlWorkOrderTypeCatalog(
        repository,
        mapper,
        new DdlWorkOrderTypeSpecificationRegistry(mapper),
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

  private static ApprovalTypeDefinition definition(String generator, String rules) {
    Instant now = Instant.now();
    return new ApprovalTypeDefinition(
        "id",
        "tenant",
        "TYPE_KEY",
        "CLICKHOUSE_DDL",
        "{}",
        "{}",
        generator,
        "[]",
        rules,
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

  private static String protectedRules() {
    return "{\"protectKeys\":[\"sorting_key\",\"primary_key\",\"partition_key\",\"sampling_key\"],\"allowPromptOverride\":false}";
  }
}
