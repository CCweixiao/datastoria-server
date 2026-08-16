package io.github.ccweixiao.datastoria.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Verifies the Java classpath is the complete, self-contained source for AgentScope Skill bundles.
 * The frontend deliberately has no Skill source tree or execution catalog.
 */
class SkillCatalogSemanticDiffTest {

  private final SkillCatalog catalog =
      new SkillCatalog(List.of(new ClasspathSkillBundleProvider()));

  @Test
  void javaClasspathContainsCompleteBuiltinCatalog() {
    var bundles = catalog.list();

    assertThat(bundles)
        .extracting(SkillBundle::id)
        .containsExactlyInAnyOrderElementsOf(
            Set.of(
                "clickhouse",
                "clickhouse-system-queries",
                "diagnose-clickhouse-clusters",
                "diagnose-clickhouse-errors",
                "optimize-clickhouse-sql",
                "source-code-inspection",
                "sql-expert",
                "visualization",
                "vizlayer"));
    for (SkillBundle bundle : bundles) {
      assertThat(bundle.skillMarkdown()).as("%s/SKILL.md", bundle.id()).isNotBlank();
      assertThat(bundle.checksum()).as("%s checksum", bundle.id()).hasSize(64);
      assertThat(bundle.resources())
          .as("%s resource paths", bundle.id())
          .allSatisfy(
              (path, content) -> {
                assertThat(path).doesNotStartWith("/").doesNotContain("..");
                assertThat(content).isNotBlank();
              });
    }
  }
}
