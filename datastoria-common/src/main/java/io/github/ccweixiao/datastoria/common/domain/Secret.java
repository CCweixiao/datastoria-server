package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/**
 * Encrypted secret. The plaintext value is never stored; only the AES-GCM cipher text, nonce, key
 * version and a masked hint for display.
 */
public record Secret(
    String id,
    String tenantId,
    String ownerUserId,
    String secretKind,
    byte[] cipherText,
    String keyVersion,
    byte[] nonce,
    String maskedHint,
    Instant expiresAt,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt) {}
