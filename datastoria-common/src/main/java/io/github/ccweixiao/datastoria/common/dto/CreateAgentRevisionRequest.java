package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRevisionRequest(
    String modelId,
    @NotBlank String systemPrompt,
    String runtimeConfigJson,
    String toolPolicyJson,
    String skillPolicyJson) {}
