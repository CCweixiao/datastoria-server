package io.github.ccweixiao.datastoria.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ccweixiao.datastoria.common.domain.AgentSkill;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser;

class SlashCommandExpanderTest {

  private final SlashCommandExpander expander = new SlashCommandExpander(new SkillMetadataParser());

  @Test
  void expandsEnabledSkillCommandAndPreservesUnknownCommand() {
    AgentSkill enabled = skill("bundle-id", "command-name", false);

    assertThat(expander.expand("/command-name inspect this", List.of(enabled)))
        .isEqualTo("Use the `command-name` skill for this request: inspect this");
    assertThat(expander.expand("/unknown inspect this", List.of(enabled)))
        .isEqualTo("/unknown inspect this");
  }

  @Test
  void doesNotExpandDisabledSlashCommand() {
    AgentSkill disabled = skill("hidden", "hidden", true);

    assertThat(expander.expand("/hidden inspect this", List.of(disabled)))
        .isEqualTo("/hidden inspect this");
  }

  private static AgentSkill skill(String id, String name, boolean disabled) {
    String content =
        """
        ---
        name: %s
        description: test
        metadata:
          disable-slash-command: %s
        ---
        # Test
        """
            .formatted(name, disabled);
    return new AgentSkill(
        id,
        "tenant",
        "user",
        content,
        "published",
        "tenant",
        "1",
        "sum",
        false,
        1,
        Instant.EPOCH,
        Instant.EPOCH,
        null);
  }
}
