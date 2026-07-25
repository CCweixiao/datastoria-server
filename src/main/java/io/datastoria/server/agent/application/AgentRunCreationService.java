package io.datastoria.server.agent.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunSkillPin;
import io.datastoria.server.repository.AgentRunRepository;
import io.datastoria.server.repository.AgentRunSkillRepository;

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
