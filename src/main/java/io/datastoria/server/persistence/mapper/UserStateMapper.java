package io.datastoria.server.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.UserStateEntity;

/** Mapper for {@code ds_user_state}; the CAS/race-retry upsert lives in the adapter. */
public interface UserStateMapper extends BaseMapper<UserStateEntity> {

  List<UserStateEntity> findAll(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("namespace") String namespace);

  UserStateEntity find(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("namespace") String namespace,
      @Param("stateKey") String stateKey);

  int casUpdate(
      @Param("entity") UserStateEntity entity,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);

  int updateNoRevisionCheck(@Param("entity") UserStateEntity entity, @Param("now") Instant now);

  int insert(@Param("entity") UserStateEntity entity, @Param("now") Instant now);

  int delete(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("namespace") String namespace,
      @Param("stateKey") String stateKey);
}
