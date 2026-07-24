package io.datastoria.server.domain;

import java.time.Instant;

/**
 * A single configuration entry scoped at system, tenant, or user level. The effective configuration
 * for a user is the merge of all three layers where user wins over tenant, which wins over system.
 */
public record ConfigEntry(
    String id,
    String tenantId,
    String scopeType,
    String scopeId,
    String configKey,
    String valueJson,
    String schemaVersion,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
