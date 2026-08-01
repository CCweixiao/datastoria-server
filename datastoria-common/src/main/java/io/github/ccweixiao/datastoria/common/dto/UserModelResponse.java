package io.github.ccweixiao.datastoria.common.dto;

import io.github.ccweixiao.datastoria.common.domain.Model;

/** Private model metadata. API key plaintext is never returned. */
public record UserModelResponse(
    String id,
    String providerId,
    String modelKey,
    String displayName,
    String description,
    boolean enabled,
    boolean credentialConfigured,
    String maskedHint,
    long revision) {

  public static UserModelResponse from(Model model, String maskedHint) {
    return new UserModelResponse(
        model.id(),
        model.providerId(),
        model.modelKey(),
        model.displayName(),
        model.description(),
        model.enabled(),
        model.secretId() != null,
        maskedHint,
        model.revision());
  }
}
