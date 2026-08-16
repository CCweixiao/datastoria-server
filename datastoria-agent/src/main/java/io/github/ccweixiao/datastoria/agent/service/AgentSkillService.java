package io.github.ccweixiao.datastoria.agent.service;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.agent.skill.SkillBundle;
import io.github.ccweixiao.datastoria.agent.skill.SkillCatalog;
import io.github.ccweixiao.datastoria.agent.skill.SkillToolAvailability;
import io.github.ccweixiao.datastoria.common.dto.CommandResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillCatalogResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillDetailResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillResourceResponse;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser.ParsedSkillMetadata;

/**
 * Read-only view over the deployment's static {@link SkillCatalog}. Skills ship in the jar and are
 * never authored at runtime, so every read is an in-memory lookup — no database, no provisioning.
 */
@Service
public class AgentSkillService {

  private static final String SOURCE = "builtin";
  private static final String STATE_PUBLISHED = "published";
  private static final String SCOPE_GLOBAL = "global";

  private final SkillCatalog catalog;
  private final SkillMetadataParser metadataParser;
  private final SkillToolAvailability toolAvailability;

  public AgentSkillService(
      SkillCatalog catalog,
      SkillMetadataParser metadataParser,
      SkillToolAvailability toolAvailability) {
    this.catalog = catalog;
    this.metadataParser = metadataParser;
    this.toolAvailability = toolAvailability;
  }

  public List<SkillCatalogResponse> list() {
    return catalog.list().stream()
        .map(skill -> catalog(skill, !skill.resources().isEmpty()))
        .toList();
  }

  public SkillDetailResponse detail(String id) {
    SkillBundle skill = require(id);
    ParsedSkillMetadata metadata = metadata(skill);
    return new SkillDetailResponse(
        skill.id(),
        metadata.name(),
        metadata.description(),
        SOURCE,
        status(skill),
        STATE_PUBLISHED,
        SCOPE_GLOBAL,
        skill.version(),
        metadata.author() == null ? "" : metadata.author(),
        metadata.url(),
        metadata.summary(),
        !skill.resources().isEmpty(),
        metadata.disableSlashCommand(),
        metadata.showInSqlEditorQuickAction(),
        metadata.requiredTools(),
        skill.skillMarkdown(),
        List.copyOf(skill.resources().keySet()));
  }

  public SkillResourceResponse resource(String id, String path) {
    SkillBundle skill = require(id);
    String content =
        catalog
            .findResource(id, path)
            .orElseThrow(() -> new NotFoundException("SkillResource", path));
    String author = metadata(skill).author();
    return new SkillResourceResponse(
        content,
        SOURCE,
        STATE_PUBLISHED,
        SCOPE_GLOBAL,
        author == null ? "" : author,
        skill.version());
  }

  public List<CommandResponse> commands() {
    return catalog.list().stream()
        .filter(skill -> "available".equals(status(skill)))
        .map(skill -> java.util.Map.entry(skill, metadata(skill)))
        .filter(entry -> !entry.getValue().disableSlashCommand())
        .filter(entry -> entry.getValue().name().matches("[a-z][a-z0-9_-]{0,254}"))
        .map(
            entry ->
                new CommandResponse(
                    entry.getValue().name(),
                    entry.getValue().description(),
                    entry.getKey().id(),
                    entry.getValue().showInSqlEditorQuickAction(),
                    "Use the `" + entry.getValue().name() + "` skill for this request: $ARGUMENTS"))
        .toList();
  }

  private SkillBundle require(String id) {
    return catalog.find(id).orElseThrow(() -> new NotFoundException("Skill", id));
  }

  private String status(SkillBundle skill) {
    return toolAvailability.isAvailable(skill.skillMarkdown(), skill.id())
        ? "available"
        : "disabled";
  }

  private ParsedSkillMetadata metadata(SkillBundle skill) {
    return metadataParser.parse(skill.skillMarkdown(), skill.id());
  }

  private SkillCatalogResponse catalog(SkillBundle skill, boolean hasResources) {
    ParsedSkillMetadata metadata = metadata(skill);
    return new SkillCatalogResponse(
        skill.id(),
        metadata.name(),
        metadata.description(),
        SOURCE,
        status(skill),
        STATE_PUBLISHED,
        SCOPE_GLOBAL,
        skill.version(),
        metadata.author() == null ? "" : metadata.author(),
        metadata.url(),
        metadata.summary(),
        hasResources,
        metadata.disableSlashCommand(),
        metadata.showInSqlEditorQuickAction(),
        metadata.requiredTools());
  }
}
