package io.github.ccweixiao.datastoria.common.domain.approval;

import java.time.Instant;

public record ApprovalTypeDefinition(
    String id,
    String tenantId,
    String typeKey,
    String handlerKey,
    String nameI18nJson,
    String descriptionI18nJson,
    String generatorKey,
    String allowedOperationKindsJson,
    String generationRuleJson,
    String applicableConnectionsJson,
    String riskPolicyJson,
    String status,
    long definitionRevision,
    String checksum,
    String createdBy,
    String updatedBy,
    String enabledBy,
    Instant createdAt,
    Instant updatedAt,
    Instant enabledAt) {}
