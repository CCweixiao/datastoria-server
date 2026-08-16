package io.github.ccweixiao.datastoria.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser;

class SlashCommandExpanderTest {

  private final SlashCommandExpander expander = new SlashCommandExpander(new SkillMetadataParser());

  @Test
  void expandsEnabledSkillCommandAndPreservesUnknownCommand() {
    SkillBundle enabled = skill("bundle-id", "command-name", false);

    assertThat(expander.expand("/command-name inspect this", List.of(enabled)))
        .isEqualTo("Use the `command-name` skill for this request: inspect this");
    assertThat(expander.expand("/unknown inspect this", List.of(enabled)))
        .isEqualTo("/unknown inspect this");
  }

  @Test
  void doesNotExpandDisabledSlashCommand() {
    SkillBundle disabled = skill("hidden", "hidden", true);

    assertThat(expander.expand("/hidden inspect this", List.of(disabled)))
        .isEqualTo("/hidden inspect this");
  }

  private static SkillBundle skill(String id, String name, boolean disabled) {
    String markdown =
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
    return new SkillBundle(
        id, name, "test", "1", markdown, Map.of(), List.of(), Map.of(), "checksum");
  }
}
