package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRequest(
    @NotBlank String agentKey, @NotBlank String name, String description) {}
