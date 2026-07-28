package io.github.ccweixiao.datastoria.common.dto;

import java.util.List;

/** Response for POST /api/ai/models/available. */
public record AvailableModelsResponse(
    List<ModelProps> systemModels, List<ModelProps> githubModels, List<ModelProps> codexModels) {

  public static AvailableModelsResponse systemOnly(List<ModelProps> systemModels) {
    return new AvailableModelsResponse(systemModels, List.of(), List.of());
  }
}
