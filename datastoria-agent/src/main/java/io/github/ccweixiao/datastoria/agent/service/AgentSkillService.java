package io.github.ccweixiao.datastoria.agent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.agent.skill.BuiltinSkillProvisioner;
import io.github.ccweixiao.datastoria.agent.skill.SkillToolAvailability;
import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.AgentSkill;
import io.github.ccweixiao.datastoria.common.domain.AgentSkillResource;
import io.github.ccweixiao.datastoria.common.dto.CommandResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillCatalogResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillDetailResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillResourceRequest;
import io.github.ccweixiao.datastoria.common.dto.SkillResourceResponse;
import io.github.ccweixiao.datastoria.common.dto.UpsertSkillRequest;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.ResourceInUseException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser.ParsedSkillMetadata;
import io.github.ccweixiao.datastoria.dao.repository.AgentSkillRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class AgentSkillService {

  private static final Pattern SAFE_ID = Pattern.compile("[a-z][a-z0-9_-]{0,254}");

  private final AgentSkillRepository repository;
  private final BuiltinSkillProvisioner builtinSkillProvisioner;
  private final SkillMetadataParser metadataParser;
  private final SkillToolAvailability toolAvailability;
  private final Scheduler jdbcScheduler;

  public AgentSkillService(
      AgentSkillRepository repository,
      BuiltinSkillProvisioner builtinSkillProvisioner,
      SkillMetadataParser metadataParser,
      SkillToolAvailability toolAvailability,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.repository = repository;
    this.builtinSkillProvisioner = builtinSkillProvisioner;
    this.metadataParser = metadataParser;
    this.toolAvailability = toolAvailability;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<SkillCatalogResponse>> list(Identity identity, boolean includeDraft) {
    return Mono.fromCallable(
            () -> {
              builtinSkillProvisioner.provision(identity.tenantId());
              return repository
                  .findVisible(identity.tenantId(), identity.userId(), includeDraft)
                  .stream()
                  .map(
                      skill ->
                          catalog(
                              skill,
                              !repository
                                  .findResources(skill.tenantId(), skill.id(), skill.revision())
                                  .isEmpty()))
                  .toList();
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<SkillDetailResponse> detail(String id, Identity identity, boolean includeDraft) {
    return Mono.fromCallable(
            () -> {
              builtinSkillProvisioner.provision(identity.tenantId());
              AgentSkill skill = require(id, identity, includeDraft);
              List<String> paths =
                  repository.findResources(skill.tenantId(), skill.id(), skill.revision()).stream()
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
                  catalog.url(),
                  catalog.summary(),
                  catalog.hasResources(),
                  catalog.disableSlashCommand(),
                  catalog.showInSqlEditorQuickAction(),
                  catalog.requiredTools(),
                  identity.isAdmin() && !skill.builtin(),
                  skill.content(),
                  paths);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<SkillResourceResponse> resource(
      String id, String path, Identity identity, boolean includeDraft) {
    return Mono.fromCallable(
            () -> {
              builtinSkillProvisioner.provision(identity.tenantId());
              AgentSkill skill = require(id, identity, includeDraft);
              AgentSkillResource resource =
                  repository.findResources(skill.tenantId(), skill.id(), skill.revision()).stream()
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
              metadataParser.parse(request.content(), id);
              builtinSkillProvisioner.provision(identity.tenantId());
              rejectBuiltinMutation(id, identity);
              AgentSkill existing =
                  repository
                      .findById(identity.tenantId(), identity.userId(), id, true)
                      .orElse(null);
              LinkedHashMap<String, AgentSkillResource> resources = new LinkedHashMap<>();
              if (existing != null) {
                repository
                    .findResources(existing.tenantId(), existing.id(), existing.revision())
                    .forEach(resource -> resources.put(resource.path(), resource));
              }
              List<String> deletedPaths = safe(request.deletedResourcePaths());
              deletedPaths.forEach(AgentSkillService::validateResourcePath);
              deletedPaths.forEach(resources::remove);
              for (SkillResourceRequest resource : safe(request.resources())) {
                validateResourcePath(resource.path());
                resources.put(
                    resource.path(),
                    new AgentSkillResource(
                        identity.tenantId(), id, resource.path(), resource.content(), null, null));
              }
              repository.saveBundle(
                  new AgentSkill(
                      id,
                      identity.tenantId(),
                      identity.userId(),
                      request.content(),
                      request.action() != null
                          ? "published"
                          : defaultValue(request.state(), "draft"),
                      defaultValue(request.scope(), existing == null ? "self" : existing.scope()),
                      defaultValue(request.version(), existing == null ? null : existing.version()),
                      null,
                      false,
                      existing == null ? 0 : existing.revision(),
                      null,
                      null,
                      null),
                  List.copyOf(resources.values()));
            })
        .subscribeOn(jdbcScheduler)
        .then();
  }

  public Mono<Void> publish(String id, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              builtinSkillProvisioner.provision(identity.tenantId());
              rejectBuiltinMutation(id, identity);
              repository.publish(identity.tenantId(), identity.userId(), id);
            })
        .subscribeOn(jdbcScheduler)
        .then();
  }

  public Mono<Void> delete(String id, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              builtinSkillProvisioner.provision(identity.tenantId());
              rejectBuiltinMutation(id, identity);
              repository.delete(identity.tenantId(), identity.userId(), id);
            })
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
                            "available".equals(skill.status())
                                && !skill.disableSlashCommand()
                                && SAFE_ID.matcher(skill.name()).matches())
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

  private void rejectBuiltinMutation(String id, Identity identity) {
    repository
        .findById(identity.tenantId(), identity.userId(), id, true)
        .filter(AgentSkill::builtin)
        .ifPresent(
            ignored -> {
              throw new ResourceInUseException("BuiltinAgentSkill", id);
            });
  }

  private SkillCatalogResponse catalog(AgentSkill skill, boolean hasResources) {
    ParsedSkillMetadata metadata = metadataParser.parse(skill.content(), skill.id());
    return new SkillCatalogResponse(
        skill.id(),
        metadata.name(),
        metadata.description(),
        "database",
        toolAvailability.isAvailable(skill.content(), skill.id()) ? "available" : "disabled",
        skill.state(),
        skill.scope(),
        skill.version(),
        metadata.author() == null ? skill.ownerUserId() : metadata.author(),
        metadata.url(),
        metadata.summary(),
        hasResources,
        metadata.disableSlashCommand(),
        metadata.showInSqlEditorQuickAction(),
        metadata.requiredTools());
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
}
