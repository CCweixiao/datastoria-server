package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentRunSkillEntity;

/** Mapper for {@code ds_agent_run_skill} (composite key, pinned skills for a run). */
public interface AgentRunSkillMapper {

  int insertAll(@Param("pins") List<AgentRunSkillEntity> pins);

  List<AgentRunSkillEntity> findByRun(
      @Param("tenantId") String tenantId, @Param("runId") String runId);
}
