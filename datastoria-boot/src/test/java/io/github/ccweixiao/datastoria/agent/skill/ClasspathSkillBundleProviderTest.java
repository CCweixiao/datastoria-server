package io.github.ccweixiao.datastoria.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClasspathSkillBundleProviderTest {

  private final ClasspathSkillBundleProvider provider = new ClasspathSkillBundleProvider();
  private final SkillCatalog catalog = new SkillCatalog(List.of(provider));

  @Test
  void discoversAndValidatesAllBuiltinBundles() {
    List<SkillBundle> bundles = catalog.list();

    assertThat(bundles)
        .extracting(SkillBundle::id)
        .containsExactly(
            "clickhouse",
            "clickhouse-system-queries",
            "diagnose-clickhouse-clusters",
            "diagnose-clickhouse-errors",
            "optimize-clickhouse-sql",
            "source-code-inspection",
            "sql-expert",
            "visualization",
            "vizlayer");
    assertThat(bundles).allSatisfy(bundle -> assertThat(bundle.checksum()).hasSize(64));
    assertThat(catalog.find("source-code-inspection").orElseThrow().requiredTools())
        .containsExactly("search_file", "read_file");
    assertThat(catalog.find("clickhouse").orElseThrow().resources())
        .containsKey("rules/schema-pk-plan-before-creation.md");
    assertThat(catalog.findResource("clickhouse", "rules/schema-pk-plan-before-creation.md"))
        .isPresent();
    assertThat(catalog.find("does-not-exist")).isEmpty();
  }
}
