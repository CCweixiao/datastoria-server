package io.datastoria.server.agent.runtime;

import java.util.List;

import io.agentscope.core.skill.AgentSkill;

/** Run-scoped AgentScope skills and tools resolved by the application layer. */
public record AgentRunCapabilities(List<AgentSkill> skills, List<Object> tools) {

  public AgentRunCapabilities {
    skills = skills == null ? List.of() : List.copyOf(skills);
    tools = tools == null ? List.of() : tools.stream().filter(java.util.Objects::nonNull).toList();
  }

  public AgentRunCapabilities(List<AgentSkill> skills, Object tools) {
    this(skills, tools == null ? List.of() : List.of(tools));
  }

  public static AgentRunCapabilities none() {
    return new AgentRunCapabilities(List.of(), List.of());
  }
}
