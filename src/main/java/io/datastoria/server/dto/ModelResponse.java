package io.datastoria.server.dto;

import java.time.Instant;

import io.datastoria.server.domain.Model;

public record ModelResponse(
    String id,
    String providerId,
    String modelKey,
    String displayName,
    String description,
    String source,
    boolean enabled,
    boolean isFree,
    String capabilitiesJson,
    String generationDefaultsJson,
    long revision,
    Instant createdAt,
    Instant updatedAt) {

  public static ModelResponse from(Model m) {
    return new ModelResponse(
        m.id(),
        m.providerId(),
        m.modelKey(),
        m.displayName(),
        m.description(),
        m.source(),
        m.enabled(),
        m.isFree(),
        m.capabilitiesJson(),
        m.generationDefaultsJson(),
        m.revision(),
        m.createdAt(),
        m.updatedAt());
  }
}
