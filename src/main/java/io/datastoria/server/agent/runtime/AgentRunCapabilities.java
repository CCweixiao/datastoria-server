package io.datastoria.server.agent.runtime;

import java.util.List;

import io.agentscope.core.skill.AgentSkill;

/** Run-scoped AgentScope skills and tools resolved by the application layer. */
public record AgentRunCapabilities(List<AgentSkill> skills, Object tools) {

  public AgentRunCapabilities {
    skills = skills == null ? List.of() : List.copyOf(skills);
  }

  public static AgentRunCapabilities none() {
    return new AgentRunCapabilities(List.of(), null);
  }
}
