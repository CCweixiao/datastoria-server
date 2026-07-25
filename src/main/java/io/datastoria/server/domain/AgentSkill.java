package io.datastoria.server.domain;

import java.time.Instant;

public record AgentSkill(
    String id,
    String tenantId,
    String ownerUserId,
    String content,
    String state,
    String scope,
    String version,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
