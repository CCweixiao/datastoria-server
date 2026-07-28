package io.github.ccweixiao.datastoria.common.dto;

import java.time.Instant;

public record OAuthCredentialResponse(
    String provider, boolean configured, String tokenType, String scope, Instant expiresAt) {}
