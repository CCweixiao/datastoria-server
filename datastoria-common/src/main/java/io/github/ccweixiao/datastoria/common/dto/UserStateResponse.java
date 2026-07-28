package io.github.ccweixiao.datastoria.common.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record UserStateResponse(
    String key, JsonNode value, long revision, Instant createdAt, Instant updatedAt) {}
