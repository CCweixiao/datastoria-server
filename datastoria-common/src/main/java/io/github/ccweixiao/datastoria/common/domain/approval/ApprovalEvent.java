package io.github.ccweixiao.datastoria.common.domain.approval;

import java.time.Instant;

public record ApprovalEvent(
    String id,
    String tenantId,
    String requestId,
    String eventType,
    String actorUserId,
    String actorDisplayName,
    String safeMessage,
    String detailsJson,
    Instant createdAt) {}
