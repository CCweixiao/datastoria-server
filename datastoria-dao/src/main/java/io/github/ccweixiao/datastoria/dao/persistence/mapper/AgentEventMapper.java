package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentEventEntity;

/** Mapper for {@code ds_agent_event} (append-only replay frames). */
public interface AgentEventMapper extends BaseMapper<AgentEventEntity> {

  void append(AgentEventEntity entity);

  Long maxSequence(@Param("tenantId") String tenantId, @Param("runId") String runId);

  List<AgentEventEntity> findAfter(
      @Param("tenantId") String tenantId,
      @Param("runId") String runId,
      @Param("sequence") long sequence);
}
