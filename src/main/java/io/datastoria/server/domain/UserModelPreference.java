package io.datastoria.server.domain;

import java.time.Instant;

/** A user's selected model and accompanying preference payload. Unique per (tenantId, userId). */
public record UserModelPreference(
    String id,
    String tenantId,
    String userId,
    String selectedModelId,
    String preferenceJson,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
