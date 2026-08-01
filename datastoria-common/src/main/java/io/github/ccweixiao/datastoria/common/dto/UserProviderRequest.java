package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Creates or updates a private model provider owned by the authenticated user. */
public record UserProviderRequest(
    @NotBlank @Size(max = 64) String providerKey,
    @NotBlank @Size(max = 128) String displayName,
    @NotBlank @Size(max = 1024) @Pattern(regexp = "https?://.+") String baseUrl,
    @Size(max = 4096) String apiKey) {}
