package io.datastoria.server.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAgentRequest(@NotBlank String name, String description) {}
