package io.datastoria.server.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record UserStateResponse(
    String key, JsonNode value, long revision, Instant createdAt, Instant updatedAt) {}
