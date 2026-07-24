package io.datastoria.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateModelRequest(
    @NotBlank String providerId,
    @NotBlank String modelKey,
    @NotBlank String displayName,
    String description,
    @NotBlank
        @Pattern(
            regexp = "system|discovered|custom",
            message = "source must be system, discovered or custom")
        String source,
    Boolean enabled,
    Boolean isFree,
    String capabilitiesJson,
    String generationDefaultsJson) {}
