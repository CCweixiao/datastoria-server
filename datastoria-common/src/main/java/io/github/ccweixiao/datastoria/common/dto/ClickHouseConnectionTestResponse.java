package io.github.ccweixiao.datastoria.common.dto;

public record ClickHouseConnectionTestResponse(boolean ok, long latencyMs, String message) {}
