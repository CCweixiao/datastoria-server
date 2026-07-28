package io.github.ccweixiao.datastoria.agent.skill;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.ccweixiao.datastoria.common.domain.AgentSkill;
import io.github.ccweixiao.datastoria.common.domain.AgentSkillResource;
import io.github.ccweixiao.datastoria.dao.repository.AgentSkillRepository;

/**
 * Idempotently imports the version-controlled Skill baseline into the active tenant database.
 *
 * <p>The classpath is an installation source only. Once provisioned, catalog, detail, resources and
 * Agent runtime reads all use {@link AgentSkillRepository}.
 */
@Service
public class BuiltinSkillProvisioner {

  static final String SYSTEM_OWNER = "__system_skill_seed__";

  private final AgentSkillRepository repository;
  private final ClasspathSkillBundleLoader loader;
  private final boolean enabled;
  private final Set<String> provisionedTenants = new HashSet<>();

  public BuiltinSkillProvisioner(
      AgentSkillRepository repository,
      ClasspathSkillBundleLoader loader,
      @Value("${datastoria.skills.seed-enabled:true}") boolean enabled) {
    this.repository = repository;
    this.loader = loader;
    this.enabled = enabled;
  }

  @Transactional
  public synchronized void provision(String tenantId) {
    if (!enabled || provisionedTenants.contains(tenantId)) {
      return;
    }
    for (SkillBundle bundle : loader.loadAll()) {
      AgentSkill existing =
          repository.findById(tenantId, SYSTEM_OWNER, bundle.id(), true).orElse(null);
      // A tenant-authored database Skill with the same id has explicit precedence over the
      // installation seed. Never overwrite it or turn it into a built-in bundle.
      if (existing != null && !existing.builtin()) {
        continue;
      }
      if (existing != null
          && existing.builtin()
          && bundle.checksum().equals(existing.bundleChecksum())) {
        continue;
      }
      repository.saveBundle(
          new AgentSkill(
              bundle.id(),
              tenantId,
              SYSTEM_OWNER,
              bundle.skillMarkdown(),
              "published",
              "global",
              bundle.version(),
              bundle.checksum(),
              true,
              existing == null ? 0 : existing.revision(),
              existing == null ? null : existing.createdAt(),
              null,
              null),
          bundle.resources().entrySet().stream()
              .map(
                  entry ->
                      new AgentSkillResource(
                          tenantId, bundle.id(), entry.getKey(), entry.getValue(), null, null))
              .toList());
    }
    provisionedTenants.add(tenantId);
  }
}
