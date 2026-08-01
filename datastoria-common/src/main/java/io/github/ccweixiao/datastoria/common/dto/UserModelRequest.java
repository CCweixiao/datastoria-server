package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Creates or updates a model visible only to the authenticated user. */
public record UserModelRequest(
    @NotBlank String providerId,
    @NotBlank @Size(max = 255) String modelKey,
    @NotBlank @Size(max = 255) String displayName,
    String description,
    @Size(max = 4096) String apiKey) {}
