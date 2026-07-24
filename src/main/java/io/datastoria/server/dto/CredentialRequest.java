package io.datastoria.server.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Credential write request. The {@code value} field is the plaintext secret — it is never logged,
 * persisted as-is, or returned in any API response. Only the encrypted envelope is stored.
 */
public record CredentialRequest(
    @NotBlank
        @Pattern(
            regexp = "api_key|access_token|refresh_token",
            message = "secretKind must be api_key, access_token or refresh_token")
        String secretKind,
    @NotBlank String value,
    Instant expiresAt) {}
