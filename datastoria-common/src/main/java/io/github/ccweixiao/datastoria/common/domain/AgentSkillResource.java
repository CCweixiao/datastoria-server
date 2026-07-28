package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

public record AgentSkillResource(
    String tenantId,
    String skillId,
    String path,
    String content,
    Instant createdAt,
    Instant updatedAt) {}
