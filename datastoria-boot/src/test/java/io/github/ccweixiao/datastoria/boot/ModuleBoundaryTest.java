package io.github.ccweixiao.datastoria.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

  private static final String BASE_PACKAGE = "io.github.ccweixiao.datastoria.";
  private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([^;]+);");
  private static final Pattern IMPORT =
      Pattern.compile("(?m)^import\\s+io\\.github\\.ccweixiao\\.datastoria\\.([^.]+)");

  private static final Map<String, Set<String>> ALLOWED_IMPORTS =
      new LinkedHashMap<>(
          Map.of(
              "common", Set.of("common"),
              "dao", Set.of("common", "dao"),
              "service", Set.of("common", "dao", "service"),
              "agent", Set.of("common", "dao", "service", "agent"),
              "controller", Set.of("common", "dao", "service", "agent", "controller"),
              "boot", Set.of("common", "dao", "service", "agent", "controller", "boot")));

  @Test
  void productionSourcesUseTheirModulePackageAndOnlyDependDownward() throws IOException {
    for (Map.Entry<String, Set<String>> module : ALLOWED_IMPORTS.entrySet()) {
      String name = module.getKey();
      Path sourceRoot = Path.of("datastoria-" + name, "src/main/java");
      assertThat(sourceRoot).isDirectory();
      try (var files = Files.walk(sourceRoot).filter(path -> path.toString().endsWith(".java"))) {
        for (Path file : files.toList()) {
          String source = Files.readString(file);
          Matcher packageMatcher = PACKAGE.matcher(source);
          assertThat(packageMatcher.find()).as("package declaration in %s", file).isTrue();
          assertThat(packageMatcher.group(1))
              .as("module root package in %s", file)
              .startsWith(BASE_PACKAGE + name);

          Matcher imports = IMPORT.matcher(source);
          while (imports.find()) {
            assertThat(module.getValue())
                .as("%s must not depend upward through import %s", name, imports.group())
                .contains(imports.group(1));
          }
        }
      }
    }
  }

  @Test
  void reactorUsesRequestedGroupIdAndContainsAllModules() throws IOException {
    String parent = Files.readString(Path.of("pom.xml"));
    assertThat(parent).contains("<groupId>io.github.ccweixiao.datastoria</groupId>");
    for (String module :
        List.of(
            "datastoria-common",
            "datastoria-dao",
            "datastoria-service",
            "datastoria-agent",
            "datastoria-controller",
            "datastoria-boot")) {
      assertThat(parent).contains("<module>" + module + "</module>");
      assertThat(Path.of(module, "pom.xml")).isRegularFile();
    }
  }
}
