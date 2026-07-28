package io.github.ccweixiao.datastoria.agent.skill;

import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.agent.runtime.AgentToolRegistry;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser;

/**
 * P5 availability policy for Skill-declared tool dependencies.
 *
 * <p>The set comes from the same AgentScope Toolkit registry used for each run. Browser executors
 * remain intentionally excluded.
 */
@Component
public class SkillToolAvailability {

  private final SkillMetadataParser metadataParser;
  private final AgentToolRegistry toolRegistry;

  public SkillToolAvailability(SkillMetadataParser metadataParser, AgentToolRegistry toolRegistry) {
    this.metadataParser = metadataParser;
    this.toolRegistry = toolRegistry;
  }

  public boolean isAvailable(String skillMarkdown, String fallbackName) {
    return toolRegistry
        .availableToolNames()
        .containsAll(metadataParser.parse(skillMarkdown, fallbackName).requiredTools());
  }
}
