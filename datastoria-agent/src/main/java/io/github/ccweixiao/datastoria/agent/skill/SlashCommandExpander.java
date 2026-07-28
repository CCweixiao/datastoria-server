package io.github.ccweixiao.datastoria.agent.skill;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.domain.AgentSkill;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser.ParsedSkillMetadata;

/** Expands the browser-visible Skill slash command before a request reaches the model. */
@Component
public class SlashCommandExpander {

  private static final Pattern COMMAND =
      Pattern.compile("^/([a-z][a-z0-9_-]{0,254})(?:\\s+([\\s\\S]*))?$");

  private final SkillMetadataParser metadataParser;

  public SlashCommandExpander(SkillMetadataParser metadataParser) {
    this.metadataParser = metadataParser;
  }

  public String expand(String text, List<AgentSkill> availableSkills) {
    Matcher match = COMMAND.matcher(text.trim());
    if (!match.matches()) {
      return text;
    }
    Map<String, ParsedSkillMetadata> commands =
        availableSkills.stream()
            .map(this::metadata)
            .filter(metadata -> !metadata.disableSlashCommand())
            .filter(metadata -> COMMAND.matcher("/" + metadata.name()).matches())
            .collect(
                Collectors.toMap(
                    ParsedSkillMetadata::name, Function.identity(), (first, ignored) -> first));
    ParsedSkillMetadata command = commands.get(match.group(1));
    if (command == null) {
      return text;
    }
    String arguments = match.group(2) == null ? "" : match.group(2).trim();
    return "Use the `" + command.name() + "` skill for this request: " + arguments;
  }

  public ParsedSkillMetadata metadata(AgentSkill skill) {
    return metadataParser.parse(skill.content(), skill.id());
  }
}
