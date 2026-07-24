package io.datastoria.server.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for PUT /api/me/ai/model-preference — selects the user's active model. */
public record UserModelPreferenceRequest(@NotBlank String modelConfigId, String preferenceJson) {}
