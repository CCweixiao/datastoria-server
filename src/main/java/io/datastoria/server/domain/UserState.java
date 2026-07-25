package io.datastoria.server.domain;

import java.time.Instant;

public record UserState(
    String tenantId,
    String userId,
    String namespace,
    String key,
    String valueJson,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
