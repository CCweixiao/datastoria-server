package io.github.ccweixiao.datastoria.common.dto;

import java.util.Map;

import io.github.ccweixiao.datastoria.common.domain.EffectiveConfig;

/** Response body for GET /api/me/ai/preferences — the merged effective configuration. */
public record UserPreferenceResponse(Map<String, String> entries, long revision) {

  public static UserPreferenceResponse from(EffectiveConfig config) {
    return new UserPreferenceResponse(config.entries(), config.revision());
  }
}
