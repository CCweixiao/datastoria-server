package io.datastoria.server.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAgentRevisionRequest(
    String modelId,
    @NotBlank String systemPrompt,
    String runtimeConfigJson,
    String toolPolicyJson,
    String skillPolicyJson) {}
