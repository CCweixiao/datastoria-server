package io.github.ccweixiao.datastoria.common.dto;

public record ProviderTestResponse(boolean success, long latencyMs, String message) {}
