package io.github.ccweixiao.datastoria.common.dto;

public record DiscoveredModelResponse(
    String modelKey,
    String displayName,
    String providerKey,
    String tier,
    Boolean supportsReasoning,
    Boolean supportsImageInput,
    Integer contextWindowTokens,
    Integer maxOutputTokens) {

  public DiscoveredModelResponse(String modelKey, String displayName, String providerKey) {
    this(modelKey, displayName, providerKey, "balanced", false, false, null, null);
  }
}
