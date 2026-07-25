package io.datastoria.server.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Semantic diff at the source boundary: every Java classpath bundle must contain the same files and
 * Markdown content as the corresponding Node/frontend Skill source tree. Formatting-only
 * differences introduced by Spotless (line endings, trailing whitespace, and terminal blank lines)
 * are ignored.
 */
class SkillCatalogSemanticDiffTest {

  private final ClasspathSkillBundleLoader loader = new ClasspathSkillBundleLoader();

  @Test
  void javaBundlesMatchFrontendSkillSources() throws IOException {
    for (SkillBundle bundle : loader.loadAll()) {
      assertThat(javaFiles(bundle))
          .as("frontend/Java Skill bundle %s", bundle.id())
          .isEqualTo(frontendFiles(bundle.id()));
    }
  }

  private static Map<String, String> javaFiles(SkillBundle bundle) {
    Map<String, String> files = new LinkedHashMap<>();
    files.put("SKILL.md", normalizeMarkdown(bundle.skillMarkdown()));
    bundle.resources().forEach((path, content) -> files.put(path, normalizeMarkdown(content)));
    return files;
  }

  private static Map<String, String> frontendFiles(String id) throws IOException {
    Path root =
        switch (id) {
          case "clickhouse" -> Path.of(
              "frontend/external/clickhouse/skills/clickhouse-best-practices");
          case "vizlayer" -> Path.of("frontend/external/vizlayer/skills");
          default -> Path.of("frontend/resources/skills", id);
        };
    Map<String, String> files = new LinkedHashMap<>();
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        files.put(
            root.relativize(path).toString().replace('\\', '/'),
            normalizeMarkdown(Files.readString(path, StandardCharsets.UTF_8)));
      }
    }
    return files;
  }

  private static String normalizeMarkdown(String content) {
    return content
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
        .map(String::stripTrailing)
        .reduce("", (left, line) -> left + line + "\n")
        .stripTrailing();
  }
}
