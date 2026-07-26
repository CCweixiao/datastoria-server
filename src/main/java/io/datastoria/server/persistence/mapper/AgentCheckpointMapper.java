package io.datastoria.server.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.AgentCheckpointEntity;

/**
 * Mapper for {@code ds_agent_checkpoint}. The dialect-neutral upsert (update-then-insert with
 * retry) lives in the adapter; {@link #updateExisting} preserves {@code created_at} and updates the
 * mutable columns keyed by the composite {@code (tenant_id, run_id, sequence)}.
 */
public interface AgentCheckpointMapper extends BaseMapper<AgentCheckpointEntity> {

  int insertCheckpoint(AgentCheckpointEntity entity);

  int updateExisting(@Param("entity") AgentCheckpointEntity entity, @Param("now") Instant now);

  AgentCheckpointEntity findLatest(
      @Param("tenantId") String tenantId, @Param("runId") String runId);

  AgentCheckpointEntity findBySequence(
      @Param("tenantId") String tenantId,
      @Param("runId") String runId,
      @Param("sequence") long sequence);

  List<AgentCheckpointEntity> findAllByRun(
      @Param("tenantId") String tenantId, @Param("runId") String runId);
}
