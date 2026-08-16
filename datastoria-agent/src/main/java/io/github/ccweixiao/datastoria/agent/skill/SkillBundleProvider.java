package io.github.ccweixiao.datastoria.agent.skill;

import java.util.List;

/**
 * Source of validated {@link SkillBundle}s (Strategy SPI).
 *
 * <p>The default implementation reads the version-controlled {@code classpath:/skills} tree that
 * ships with the jar; additional sources (git, remote registry, ...) plug in as extra Spring beans
 * and are merged by {@link SkillCatalog}.
 */
public interface SkillBundleProvider {

  /** Returns every bundle this source contributes; must validate content before returning. */
  List<SkillBundle> load();
}
