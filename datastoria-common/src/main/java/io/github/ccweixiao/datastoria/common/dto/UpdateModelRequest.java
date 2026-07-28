package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateModelRequest(
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
