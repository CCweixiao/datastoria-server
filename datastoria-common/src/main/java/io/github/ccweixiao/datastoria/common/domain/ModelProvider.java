package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/** Configuration for a model provider (OpenAI, Anthropic, Google, etc.). */
public record ModelProvider(
    String id,
    String tenantId,
    String providerKey,
    String displayName,
    String baseUrl,
    String authType,
    boolean enabled,
    String configJson,
    String secretId,
    long revision,
    String createdBy,
    String updatedBy,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
