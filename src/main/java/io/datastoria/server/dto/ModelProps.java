package io.datastoria.server.dto;

import java.util.List;

/**
 * Model descriptor matching the A12 {@code ModelProps} schema in {@code openapi-baseline.yaml}.
 * Consumed by the frontend to render the model picker.
 */
public record ModelProps(
    String provider,
    String modelId,
    String description,
    boolean free,
    boolean autoSelectable,
    boolean disabled,
    List<String> supportedEndpoints,
    boolean supportsImageInput,
    boolean supportsTemperature,
    boolean supportsReasoning,
    List<String> reasoningLevels,
    String source,
    String configId) {}
