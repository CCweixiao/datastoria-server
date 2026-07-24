package io.datastoria.server.dto;

import java.time.Instant;

/**
 * Credential status response. Never contains the plaintext or cipher text — only display fields.
 */
public record CredentialResponse(boolean configured, String maskedHint, Instant updatedAt) {}
