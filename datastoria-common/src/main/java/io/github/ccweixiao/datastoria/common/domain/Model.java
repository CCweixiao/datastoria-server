package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/** A model entry in the tenant catalog, scoped to a {@link ModelProvider}. */
public record Model(
    String id,
    String tenantId,
    String providerId,
    String modelKey,
    String displayName,
    String description,
    String source,
    boolean enabled,
    boolean isFree,
    String capabilitiesJson,
    String generationDefaultsJson,
    String secretId,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
