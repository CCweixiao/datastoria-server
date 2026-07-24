package io.datastoria.server.dto;

import java.util.List;

/**
 * Response for POST /api/ai/models/available — {@code githubModels} stays empty until P10 OAuth.
 */
public record AvailableModelsResponse(
    List<ModelProps> systemModels, List<ModelProps> githubModels) {

  public static AvailableModelsResponse systemOnly(List<ModelProps> systemModels) {
    return new AvailableModelsResponse(systemModels, List.of());
  }
}
