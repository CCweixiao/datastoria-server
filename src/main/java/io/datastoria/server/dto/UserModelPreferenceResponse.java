package io.datastoria.server.dto;

import io.datastoria.server.domain.UserModelPreference;

/** Response body for GET /api/me/ai/model-preference. */
public record UserModelPreferenceResponse(
    String selectedModelId, String preferenceJson, long revision) {

  public static UserModelPreferenceResponse from(UserModelPreference pref) {
    return new UserModelPreferenceResponse(
        pref.selectedModelId(), pref.preferenceJson(), pref.revision());
  }
}
