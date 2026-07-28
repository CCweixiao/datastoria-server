package io.github.ccweixiao.datastoria.agent.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;

final class InMemoryAgentSkillRepository implements AgentSkillRepository {

  private final Map<String, AgentSkill> skills;

  InMemoryAgentSkillRepository(List<AgentSkill> skills) {
    this.skills = new LinkedHashMap<>();
    for (AgentSkill skill : skills) {
      this.skills.put(skill.getName(), skill);
    }
  }

  @Override
  public AgentSkill getSkill(String name) {
    return skills.get(name);
  }

  @Override
  public List<String> getAllSkillNames() {
    return List.copyOf(skills.keySet());
  }

  @Override
  public List<AgentSkill> getAllSkills() {
    return List.copyOf(skills.values());
  }

  @Override
  public boolean save(List<AgentSkill> ignored, boolean overwrite) {
    return false;
  }

  @Override
  public boolean delete(String ignored) {
    return false;
  }

  @Override
  public boolean skillExists(String name) {
    return skills.containsKey(name);
  }

  @Override
  public AgentSkillRepositoryInfo getRepositoryInfo() {
    return new AgentSkillRepositoryInfo("database", "datastoria", false);
  }

  @Override
  public String getSource() {
    return "datastoria-database";
  }

  @Override
  public void setWriteable(boolean ignored) {}

  @Override
  public boolean isWriteable() {
    return false;
  }
}
