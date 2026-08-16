package io.github.ccweixiao.datastoria.agent.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Immutable registry (Registry pattern) of every Skill this deployment offers. Built once at
 * startup from all {@link SkillBundleProvider}s — an invalid bundle fails boot — and never changes
 * afterwards, so readers need no locking, caching or DB access.
 */
@Component
public class SkillCatalog {

  private static final Logger log = LoggerFactory.getLogger(SkillCatalog.class);

  private final Map<String, SkillBundle> bundlesById;
  private final List<SkillBundle> orderedBundles;

  public SkillCatalog(List<SkillBundleProvider> providers) {
    Map<String, SkillBundle> merged = new LinkedHashMap<>();
    for (SkillBundleProvider provider : providers) {
      for (SkillBundle bundle : provider.load()) {
        SkillBundle existing = merged.put(bundle.id(), bundle);
        if (existing != null) {
          throw new IllegalStateException(
              "Duplicate Skill id across providers: "
                  + bundle.id()
                  + " (checksum "
                  + existing.checksum()
                  + " vs "
                  + bundle.checksum()
                  + ")");
        }
      }
    }
    this.bundlesById = Map.copyOf(merged);
    this.orderedBundles = List.copyOf(merged.values());
    log.info(
        "Skill catalog initialized: {} skill(s) from {} provider(s): {}",
        orderedBundles.size(),
        providers.size(),
        orderedBundles.stream().map(SkillBundle::id).toList());
  }

  /** All bundles in discovery order (id-sorted for the classpath provider). */
  public List<SkillBundle> list() {
    return orderedBundles;
  }

  public Optional<SkillBundle> find(String id) {
    return Optional.ofNullable(bundlesById.get(id));
  }

  public Optional<String> findResource(String id, String path) {
    return find(id).map(bundle -> bundle.resources().get(path)).filter(content -> content != null);
  }
}
