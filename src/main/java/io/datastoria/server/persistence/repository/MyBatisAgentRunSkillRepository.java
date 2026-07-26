package io.datastoria.server.persistence.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import io.datastoria.server.agent.domain.AgentRunSkillPin;
import io.datastoria.server.persistence.entity.AgentRunSkillEntity;
import io.datastoria.server.persistence.mapper.AgentRunSkillMapper;
import io.datastoria.server.repository.AgentRunSkillRepository;

/** MyBatis-Plus adapter for {@code ds_agent_run_skill}. */
@Repository
public class MyBatisAgentRunSkillRepository implements AgentRunSkillRepository {

  private final AgentRunSkillMapper mapper;

  public MyBatisAgentRunSkillRepository(AgentRunSkillMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void saveAll(List<AgentRunSkillPin> pins) {
    if (pins == null || pins.isEmpty()) {
      return;
    }
    mapper.insertAll(pins.stream().map(AgentRunSkillEntity::fromDomain).toList());
  }

  @Override
  public List<AgentRunSkillPin> findByRun(String tenantId, String runId) {
    return mapper.findByRun(tenantId, runId).stream().map(AgentRunSkillEntity::toDomain).toList();
  }
}
