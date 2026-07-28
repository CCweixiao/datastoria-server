package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/**
 * Persisted session share row. The JWT returned to the caller is NOT stored — only its SHA-256
 * hash, so revocation is possible without rotating the signing secret (see ADR-0001).
 *
 * <p>At most one row per {@code (tenantId, sessionId)} may have {@code revokedAt = null} (enforced
 * via the {@code active_key} generated column + UNIQUE constraint). Revocation frees the active
 * slot and a new share may be issued for the same session.
 */
public record SessionShare(
    String id,
    String tenantId,
    String sessionId,
    String ownerUserId,
    String tokenHash,
    Instant expiresAt,
    Instant revokedAt,
    Instant createdAt) {}
