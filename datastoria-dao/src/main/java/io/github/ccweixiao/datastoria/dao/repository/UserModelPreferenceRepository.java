package io.github.ccweixiao.datastoria.dao.repository;

import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.UserModelPreference;

/** Repository for the per-user model selection. */
public interface UserModelPreferenceRepository {

  UserModelPreference upsert(
      String tenantId, String userId, String selectedModelId, String preferenceJson, Long ifMatch);

  Optional<UserModelPreference> findByUser(String tenantId, String userId);
}
