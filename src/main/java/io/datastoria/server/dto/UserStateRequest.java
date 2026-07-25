package io.datastoria.server.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record UserStateRequest(@NotNull JsonNode value) {}
