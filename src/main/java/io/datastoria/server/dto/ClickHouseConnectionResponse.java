package io.datastoria.server.dto;

import java.time.Instant;

import io.datastoria.server.domain.ClickHouseConnection;

public record ClickHouseConnectionResponse(
    String id,
    String name,
    String url,
    String username,
    String cluster,
    boolean credentialConfigured,
    String credentialMaskedHint,
    boolean enabled,
    long revision,
    Instant createdAt,
    Instant updatedAt) {

  public static ClickHouseConnectionResponse from(ClickHouseConnection connection) {
    return new ClickHouseConnectionResponse(
        connection.id(),
        connection.name(),
        connection.url(),
        connection.username(),
        connection.cluster(),
        connection.passwordCipher() != null,
        connection.passwordMaskedHint(),
        connection.enabled(),
        connection.revision(),
        connection.createdAt(),
        connection.updatedAt());
  }
}
