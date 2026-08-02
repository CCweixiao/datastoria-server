package io.github.ccweixiao.datastoria.common.domain.approval;

import java.time.Instant;

public record ApprovalNodeExecution(
    String id,
    String tenantId,
    String executionId,
    String nodeKey,
    String host,
    Integer port,
    String status,
    Long durationMs,
    String errorCode,
    String safeMessage,
    Instant startedAt,
    Instant finishedAt,
    Instant createdAt,
    Instant updatedAt) {}
