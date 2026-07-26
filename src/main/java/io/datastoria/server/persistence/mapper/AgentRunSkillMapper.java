package io.datastoria.server.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.AgentRunSkillEntity;

/** Mapper for {@code ds_agent_run_skill} (composite key, pinned skills for a run). */
public interface AgentRunSkillMapper extends BaseMapper<AgentRunSkillEntity> {

  int insertAll(@Param("pins") List<AgentRunSkillEntity> pins);

  List<AgentRunSkillEntity> findByRun(
      @Param("tenantId") String tenantId, @Param("runId") String runId);
}
