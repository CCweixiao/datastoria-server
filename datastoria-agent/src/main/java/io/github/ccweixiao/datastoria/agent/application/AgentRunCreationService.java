package io.github.ccweixiao.datastoria.agent.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.ccweixiao.datastoria.common.agent.AgentRun;
import io.github.ccweixiao.datastoria.common.agent.AgentRunSkillPin;
import io.github.ccweixiao.datastoria.dao.repository.AgentRunRepository;
import io.github.ccweixiao.datastoria.dao.repository.AgentRunSkillRepository;

/** Atomically creates a run and records the immutable Skill revisions selected for it. */
@Service
public class AgentRunCreationService {

  private final AgentRunRepository runRepository;
  private final AgentRunSkillRepository runSkillRepository;

  public AgentRunCreationService(
      AgentRunRepository runRepository, AgentRunSkillRepository runSkillRepository) {
    this.runRepository = runRepository;
    this.runSkillRepository = runSkillRepository;
  }

  @Transactional
  public AgentRun create(AgentRun run, List<AgentRunSkillPin> skillPins) {
    AgentRun created = runRepository.create(run);
    runSkillRepository.saveAll(skillPins);
    return created;
  }
}
