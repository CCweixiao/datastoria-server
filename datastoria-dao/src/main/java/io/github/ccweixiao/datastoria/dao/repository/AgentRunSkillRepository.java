package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;

import io.github.ccweixiao.datastoria.common.agent.AgentRunSkillPin;

/** Persists the immutable Skill revisions selected by an agent run. */
public interface AgentRunSkillRepository {

  void saveAll(List<AgentRunSkillPin> pins);

  List<AgentRunSkillPin> findByRun(String tenantId, String runId);
}
