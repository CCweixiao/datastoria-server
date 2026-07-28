package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAgentRequest(@NotBlank String name, String description) {}
