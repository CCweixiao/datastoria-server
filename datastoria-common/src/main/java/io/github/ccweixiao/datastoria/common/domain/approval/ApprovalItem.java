package io.github.ccweixiao.datastoria.common.domain.approval;

import java.time.Instant;

public record ApprovalItem(
    String id,
    String tenantId,
    String requestId,
    int ordinal,
    DdlOperationKind operationKind,
    String sqlText,
    String normalizedSqlDigest,
    String objectRefsJson,
    String riskLevel,
    String warningsJson,
    String idempotencyStrategy,
    String preconditionJson,
    Instant createdAt) {}
