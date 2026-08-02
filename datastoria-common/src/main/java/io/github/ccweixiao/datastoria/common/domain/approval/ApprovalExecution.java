package io.github.ccweixiao.datastoria.common.domain.approval;

import java.time.Instant;

public record ApprovalExecution(
    String id,
    String tenantId,
    String requestId,
    String itemId,
    int attemptNo,
    int ordinal,
    String status,
    String queryId,
    Instant startedAt,
    Instant finishedAt,
    Long durationMs,
    String errorCode,
    String safeMessage,
    Instant createdAt,
    Instant updatedAt) {}
