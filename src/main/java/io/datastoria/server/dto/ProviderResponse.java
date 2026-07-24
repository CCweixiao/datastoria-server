package io.datastoria.server.dto;

import java.time.Instant;

import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.domain.Secret;

public record ProviderResponse(
    String id,
    String providerKey,
    String displayName,
    String baseUrl,
    String authType,
    boolean enabled,
    String configJson,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    boolean credentialConfigured,
    String maskedHint,
    Instant credentialUpdatedAt) {

  public static ProviderResponse from(ModelProvider p, Secret credential) {
    return new ProviderResponse(
        p.id(),
        p.providerKey(),
        p.displayName(),
        p.baseUrl(),
        p.authType(),
        p.enabled(),
        p.configJson(),
        p.revision(),
        p.createdAt(),
        p.updatedAt(),
        credential != null,
        credential != null ? credential.maskedHint() : null,
        credential != null ? credential.updatedAt() : null);
  }

  public static ProviderResponse from(ModelProvider p) {
    return from(p, null);
  }
}
