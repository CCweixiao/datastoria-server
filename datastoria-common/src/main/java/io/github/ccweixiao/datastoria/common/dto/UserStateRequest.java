package io.github.ccweixiao.datastoria.common.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record UserStateRequest(@NotNull JsonNode value) {}
