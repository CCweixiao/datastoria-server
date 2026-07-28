package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for PUT /api/me/ai/preferences — upserts a single user-scope entry. */
public record UpdateUserPreferenceRequest(@NotBlank String configKey, @NotBlank String valueJson) {}
