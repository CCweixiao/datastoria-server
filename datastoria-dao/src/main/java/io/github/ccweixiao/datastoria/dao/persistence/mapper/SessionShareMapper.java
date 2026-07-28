package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.SessionShareEntity;

/**
 * Mapper for {@code ds_session_share}. The {@code active_key} generated column is never written.
 */
public interface SessionShareMapper extends BaseMapper<SessionShareEntity> {

  int insertShare(SessionShareEntity entity);

  SessionShareEntity findActive(
      @Param("tenantId") String tenantId, @Param("sessionId") String sessionId);

  SessionShareEntity findByTokenHash(@Param("tokenHash") String tokenHash);

  int revoke(
      @Param("tenantId") String tenantId,
      @Param("sessionId") String sessionId,
      @Param("now") Instant now);
}
