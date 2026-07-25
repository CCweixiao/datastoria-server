package io.datastoria.server.dto;

public record ClickHouseConnectionTestResponse(boolean ok, long latencyMs, String message) {}
