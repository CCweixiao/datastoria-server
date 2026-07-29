package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import io.github.ccweixiao.datastoria.dao.persistence.entity.UserModelPreferenceEntity;

/** Mapper for {@code ds_user_model_preference}; the upsert lives in the adapter. */
public interface UserModelPreferenceMapper {

  UserModelPreferenceEntity findByUser(
      @Param("tenantId") String tenantId, @Param("userId") String userId);

  int casUpdate(
      @Param("entity") UserModelPreferenceEntity entity,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);

  int insert(UserModelPreferenceEntity entity);
}
