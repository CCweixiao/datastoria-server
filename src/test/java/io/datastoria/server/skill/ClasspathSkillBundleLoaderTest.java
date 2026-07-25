package io.datastoria.server.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ClasspathSkillBundleLoaderTest {

  private final ClasspathSkillBundleLoader loader = new ClasspathSkillBundleLoader();

  @Test
  void scansAndValidatesAllNineBuiltinBundles() {
    List<SkillBundle> bundles = loader.loadAll();

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
    assertThat(
            bundles.stream()
                .filter(bundle -> bundle.id().equals("source-code-inspection"))
                .findFirst()
                .orElseThrow()
                .requiredTools())
        .containsExactly("search_file", "read_file");
    assertThat(
            bundles.stream()
                .filter(bundle -> bundle.id().equals("clickhouse"))
                .findFirst()
                .orElseThrow()
                .resources())
        .containsKey("rules/schema-pk-plan-before-creation.md");
  }
}
