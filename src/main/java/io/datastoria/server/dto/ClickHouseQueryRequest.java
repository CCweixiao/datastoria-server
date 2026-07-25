package io.datastoria.server.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record ClickHouseQueryRequest(
    @NotBlank @jakarta.validation.constraints.Size(max = 1_000_000) String query,
    Map<String, Object> parameters,
    @jakarta.validation.constraints.Size(max = 255) String targetNode,
    @jakarta.validation.constraints.Size(max = 255) String targetUser) {}
