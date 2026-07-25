package io.datastoria.server.domain;

import java.time.Instant;

/** Owner-scoped reference to an encrypted OAuth token bundle. */
public record OAuthCredential(
    String id,
    String tenantId,
    String userId,
    String providerKey,
    String secretId,
    String tokenType,
    String scope,
    Instant expiresAt,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
