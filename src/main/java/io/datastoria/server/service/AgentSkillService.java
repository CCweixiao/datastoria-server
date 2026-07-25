package io.datastoria.server.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.AgentSkill;
import io.datastoria.server.domain.AgentSkillResource;
import io.datastoria.server.dto.CommandResponse;
import io.datastoria.server.dto.SkillCatalogResponse;
import io.datastoria.server.dto.SkillDetailResponse;
import io.datastoria.server.dto.SkillResourceRequest;
import io.datastoria.server.dto.SkillResourceResponse;
import io.datastoria.server.dto.UpsertSkillRequest;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.AgentSkillRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class AgentSkillService {

  private static final Pattern FRONTMATTER =
      Pattern.compile("\\A---\\s*\\R([\\s\\S]*?)\\R---\\s*(?:\\R|\\z)");
  private static final Pattern FIELD =
      Pattern.compile("(?m)^([A-Za-z][A-Za-z0-9_-]*):\\s*(.*?)\\s*$");
  private static final Pattern SAFE_ID = Pattern.compile("[a-z][a-z0-9_-]{0,254}");

  private final AgentSkillRepository repository;
  private final Scheduler jdbcScheduler;

  public AgentSkillService(
      AgentSkillRepository repository,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.repository = repository;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<SkillCatalogResponse>> list(Identity identity, boolean includeDraft) {
    return Mono.fromCallable(
            () ->
                repository
                    .findVisible(identity.tenantId(), identity.userId(), includeDraft)
                    .stream()
                    .map(
                        skill ->
                            catalog(
                                skill,
                                !repository.findResources(skill.tenantId(), skill.id()).isEmpty()))
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<SkillDetailResponse> detail(String id, Identity identity, boolean includeDraft) {
    return Mono.fromCallable(
            () -> {
              AgentSkill skill = require(id, identity, includeDraft);
              List<String> paths =
                  repository.findResources(skill.tenantId(), skill.id()).stream()
                      .map(AgentSkillResource::path)
                      .toList();
              SkillCatalogResponse catalog = catalog(skill, !paths.isEmpty());
              return new SkillDetailResponse(
                  catalog.id(),
                  catalog.name(),
                  catalog.description(),
                  catalog.source(),
                  catalog.status(),
                  catalog.state(),
                  catalog.scope(),
                  catalog.version(),
                  catalog.author(),
                  catalog.summary(),
                  catalog.hasResources(),
                  catalog.disableSlashCommand(),
                  catalog.showInSqlEditorQuickAction(),
                  catalog.requiredTools(),
                  identity.isAdmin(),
                  skill.content(),
                  paths);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<SkillResourceResponse> resource(
      String id, String path, Identity identity, boolean includeDraft) {
    return Mono.fromCallable(
            () -> {
              AgentSkill skill = require(id, identity, includeDraft);
              AgentSkillResource resource =
                  repository.findResources(skill.tenantId(), skill.id()).stream()
                      .filter(candidate -> candidate.path().equals(path))
                      .findFirst()
                      .orElseThrow(() -> new NotFoundException("AgentSkillResource", path));
              return new SkillResourceResponse(
                  resource.content(),
                  "database",
                  skill.state(),
                  skill.scope(),
                  skill.ownerUserId(),
                  skill.version());
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> upsert(String id, UpsertSkillRequest request, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              validateId(id);
              AgentSkill saved =
                  repository.upsert(
                      new AgentSkill(
                          id,
                          identity.tenantId(),
                          identity.userId(),
                          request.content(),
                          request.action() != null
                              ? "published"
                              : defaultValue(request.state(), "draft"),
                          defaultValue(request.scope(), "self"),
                          request.version(),
                          0,
                          null,
                          null,
                          null));
              List<AgentSkillResource> resources = new ArrayList<>();
              for (SkillResourceRequest resource : safe(request.resources())) {
                validateResourcePath(resource.path());
                resources.add(
                    new AgentSkillResource(
                        identity.tenantId(),
                        saved.id(),
                        resource.path(),
                        resource.content(),
                        null,
                        null));
              }
              List<String> deletedPaths = safe(request.deletedResourcePaths());
              deletedPaths.forEach(AgentSkillService::validateResourcePath);
              repository.replaceResources(identity.tenantId(), saved.id(), resources, deletedPaths);
            })
        .subscribeOn(jdbcScheduler)
        .then();
  }

  public Mono<Void> publish(String id, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> repository.publish(identity.tenantId(), identity.userId(), id))
        .subscribeOn(jdbcScheduler)
        .then();
  }

  public Mono<Void> delete(String id, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> repository.delete(identity.tenantId(), identity.userId(), id))
        .subscribeOn(jdbcScheduler)
        .then();
  }

  public Mono<List<CommandResponse>> commands(Identity identity) {
    return list(identity, false)
        .map(
            skills ->
                skills.stream()
                    .filter(
                        skill ->
                            !skill.disableSlashCommand() && SAFE_ID.matcher(skill.name()).matches())
                    .map(
                        skill ->
                            new CommandResponse(
                                skill.name(),
                                skill.description(),
                                skill.id(),
                                skill.showInSqlEditorQuickAction(),
                                "Use the `"
                                    + skill.name()
                                    + "` skill for this request: $ARGUMENTS"))
                    .toList());
  }

  private AgentSkill require(String id, Identity identity, boolean includeDraft) {
    return repository
        .findById(identity.tenantId(), identity.userId(), id, includeDraft)
        .orElseThrow(() -> new NotFoundException("AgentSkill", id));
  }

  private SkillCatalogResponse catalog(AgentSkill skill, boolean hasResources) {
    Metadata metadata = metadata(skill.content(), skill.id());
    return new SkillCatalogResponse(
        skill.id(),
        metadata.name(),
        metadata.description(),
        "database",
        "available",
        skill.state(),
        skill.scope(),
        skill.version(),
        skill.ownerUserId(),
        metadata.summary(),
        hasResources,
        metadata.disableSlashCommand(),
        metadata.showInSqlEditorQuickAction(),
        metadata.requiredTools());
  }

  private static Metadata metadata(String content, String fallbackName) {
    Matcher frontmatter = FRONTMATTER.matcher(content);
    String header = frontmatter.find() ? frontmatter.group(1) : "";
    String body =
        frontmatter.find(0) ? content.substring(frontmatter.end()).trim() : content.trim();
    java.util.Map<String, String> fields = new java.util.HashMap<>();
    Matcher field = FIELD.matcher(header);
    while (field.find()) {
      fields.put(field.group(1).toLowerCase(Locale.ROOT), unquote(field.group(2)));
    }
    String summary =
        body.lines()
            .filter(line -> !line.isBlank() && !line.startsWith("#"))
            .findFirst()
            .orElse("");
    return new Metadata(
        fields.getOrDefault("name", fallbackName),
        fields.getOrDefault("description", summary),
        summary,
        Boolean.parseBoolean(fields.getOrDefault("disableslashcommand", "false")),
        Boolean.parseBoolean(fields.getOrDefault("showinsqleditorquickaction", "false")),
        List.of());
  }

  private static String unquote(String value) {
    if (value.length() >= 2
        && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static void validateId(String id) {
    if (!SAFE_ID.matcher(id).matches()) {
      throw new IllegalArgumentException("Invalid skill id");
    }
  }

  private static void validateResourcePath(String path) {
    if (path.startsWith("/")
        || path.contains("..")
        || path.contains("\\")
        || path.equals("SKILL.md")) {
      throw new IllegalArgumentException("Invalid skill resource path");
    }
  }

  private static String defaultValue(String value, String fallback) {
    return value == null ? fallback : value;
  }

  private static <T> List<T> safe(List<T> values) {
    return values == null ? List.of() : values;
  }

  private record Metadata(
      String name,
      String description,
      String summary,
      boolean disableSlashCommand,
      boolean showInSqlEditorQuickAction,
      List<String> requiredTools) {}
}
