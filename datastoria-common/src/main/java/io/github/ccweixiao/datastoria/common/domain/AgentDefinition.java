package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/** An agent definition — the mutable metadata that points at a {@link AgentRevision} for runs. */
public record AgentDefinition(
    String id,
    String tenantId,
    String agentKey,
    String name,
    String description,
    String status,
    String publishedRevisionId,
    long revision,
    String createdBy,
    String updatedBy,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
