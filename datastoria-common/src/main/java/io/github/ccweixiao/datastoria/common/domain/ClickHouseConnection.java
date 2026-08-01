package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/**
 * A user-owned ClickHouse connection. Password material is encrypted at rest and never returned.
 */
public record ClickHouseConnection(
    String id,
    String tenantId,
    String ownerUserId,
    String name,
    String url,
    String username,
    String cluster,
    String remark,
    byte[] passwordCipher,
    byte[] passwordNonce,
    String passwordKeyVersion,
    String passwordMaskedHint,
    boolean enabled,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
