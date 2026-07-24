package io.datastoria.server.dto;

import java.util.Map;

import io.datastoria.server.domain.EffectiveConfig;

/** Response body for GET /api/me/ai/preferences — the merged effective configuration. */
public record UserPreferenceResponse(Map<String, String> entries, long revision) {

  public static UserPreferenceResponse from(EffectiveConfig config) {
    return new UserPreferenceResponse(config.entries(), config.revision());
  }
}
