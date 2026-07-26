package io.datastoria.server.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.persistence.entity.AgentRunEntity;

/**
 * Mapper for {@code ds_agent_run}. {@link #transition} is the dynamic optimistic-lock UPDATE: only
 * the non-null payload fields are SET, the {@code revision} is bumped under a {@code revision =
 * expectedRevision} guard, and the adapter resolves idempotent/concurrent outcomes.
 */
public interface AgentRunMapper extends BaseMapper<AgentRunEntity> {

  int insertRun(AgentRunEntity entity);

  AgentRunEntity findByTenantAndId(@Param("tenantId") String tenantId, @Param("id") String id);

  AgentRunEntity findByIdempotencyKey(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("idempotencyKey") String idempotencyKey);

  List<AgentRunEntity> findBySession(
      @Param("tenantId") String tenantId, @Param("sessionId") String sessionId);

  AgentRunEntity findByIdInternal(@Param("id") String id);

  int transition(
      @Param("tenantId") String tenantId,
      @Param("id") String id,
      @Param("expectedRevision") long expectedRevision,
      @Param("to") String toStatus,
      @Param("now") Instant now,
      @Param("payload") RunTransition payload);
}
