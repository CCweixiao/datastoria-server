package io.datastoria.server.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.UserModelPreferenceEntity;

/** Mapper for {@code ds_user_model_preference}; the upsert lives in the adapter. */
public interface UserModelPreferenceMapper extends BaseMapper<UserModelPreferenceEntity> {

  UserModelPreferenceEntity findByUser(
      @Param("tenantId") String tenantId, @Param("userId") String userId);

  int casUpdate(
      @Param("entity") UserModelPreferenceEntity entity,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);

  int insert(UserModelPreferenceEntity entity);
}
