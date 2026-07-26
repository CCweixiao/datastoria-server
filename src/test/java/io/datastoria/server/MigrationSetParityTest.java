package io.datastoria.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Always-on structural gate for the supported Flyway dialects; runtime parity remains
 * Testcontainers.
 */
class MigrationSetParityTest {

  private static final Pattern TABLE =
      Pattern.compile("(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?[`\"]?([a-z0-9_]+)");

  @Test
  void allDialectsHaveIdenticalMigrationVersionsAndCreatedTables() throws Exception {
    Map<String, Path> sqlite = migrations("sqlite");
    Map<String, Path> mysql = migrations("mysql");

    assertThat(mysql.keySet()).isEqualTo(sqlite.keySet());
    assertThat(createdTables(mysql.values())).isEqualTo(createdTables(sqlite.values()));
    assertThat(sqlite).hasSize(15);
  }

  private Map<String, Path> migrations(String dialect) throws Exception {
    Path directory = Path.of("src/main/resources/db/migration", dialect);
    try (var files = Files.list(directory)) {
      return files
          .filter(path -> path.getFileName().toString().matches("V\\d+__.+\\.sql"))
          .collect(
              Collectors.toMap(
                  path ->
                      path.getFileName()
                          .toString()
                          .substring(0, path.getFileName().toString().indexOf("__")),
                  Function.identity()));
    }
  }

  private Set<String> createdTables(java.util.Collection<Path> migrations) {
    return migrations.stream()
        .flatMap(
            path -> {
              try {
                return TABLE.matcher(Files.readString(path)).results();
              } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
              }
            })
        .map(match -> match.group(1).toLowerCase(java.util.Locale.ROOT))
        .collect(Collectors.toSet());
  }
}
