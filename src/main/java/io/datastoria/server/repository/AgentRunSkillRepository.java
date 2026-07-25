package io.datastoria.server.repository;

import java.util.List;

import io.datastoria.server.agent.domain.AgentRunSkillPin;

/** Persists the immutable Skill revisions selected by an agent run. */
public interface AgentRunSkillRepository {

  void saveAll(List<AgentRunSkillPin> pins);

  List<AgentRunSkillPin> findByRun(String tenantId, String runId);
}
