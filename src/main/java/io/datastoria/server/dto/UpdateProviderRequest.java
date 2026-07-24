package io.datastoria.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProviderRequest(
    @NotBlank String displayName,
    String baseUrl,
    @NotBlank
        @Pattern(regexp = "api_key|oauth|none", message = "authType must be api_key, oauth or none")
        String authType,
    Boolean enabled,
    String configJson) {}
