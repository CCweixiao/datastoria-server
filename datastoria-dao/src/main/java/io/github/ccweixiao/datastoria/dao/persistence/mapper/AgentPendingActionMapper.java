package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.common.agent.PendingActionResolution;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentPendingActionEntity;

/**
 * Mapper for {@code ds_agent_pending_action}. Owner scope is enforced via a JOIN to {@code
 * ds_agent_run} on reads and an EXISTS subquery on the resolution CAS. {@link #createInsert} uses
 * INSERT...SELECT so a missing run for the (tenant, user) yields zero inserted rows.
 */
public interface AgentPendingActionMapper extends BaseMapper<AgentPendingActionEntity> {

  int createInsert(@Param("a") AgentPendingActionEntity entity, @Param("userId") String userId);

  AgentPendingActionEntity findById(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("runId") String runId,
      @Param("actionId") String actionId);

  AgentPendingActionEntity findByToolCall(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("runId") String runId,
      @Param("toolCallId") String toolCallId);

  List<AgentPendingActionEntity> findPending(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("runId") String runId);

  int resolveCas(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("runId") String runId,
      @Param("actionId") String actionId,
      @Param("statusDb") String statusDb,
      @Param("expectedRevision") long expectedRevision,
      @Param("res") PendingActionResolution resolution);

  int expireDue(@Param("now") Instant now);

  int expireOne(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);
}
