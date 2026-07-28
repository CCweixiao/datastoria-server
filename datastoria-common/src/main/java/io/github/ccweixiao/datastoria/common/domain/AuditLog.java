package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/**
 * Append-only audit record. Never contains secret plaintext or cipher text; only {@code safeDiff}
 * summaries vetted by the calling service.
 */
public record AuditLog(
    Long id,
    String tenantId,
    String actor,
    String action,
    String resourceType,
    String resourceId,
    String requestId,
    String safeDiff,
    String result,
    Instant createdAt) {}
