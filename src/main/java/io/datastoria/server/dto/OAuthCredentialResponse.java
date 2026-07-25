package io.datastoria.server.dto;

import java.time.Instant;

public record OAuthCredentialResponse(
    String provider, boolean configured, String tokenType, String scope, Instant expiresAt) {}
