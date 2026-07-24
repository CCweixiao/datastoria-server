package io.datastoria.server.dto;

public record ProviderTestResponse(boolean success, long latencyMs, String message) {}
