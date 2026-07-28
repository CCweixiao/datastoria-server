package io.github.ccweixiao.datastoria.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Architecture boundary tests for the JDBC -> MyBatis-Plus migration. These guard the invariants
 * the migration committed to:
 *
 * <ul>
 *   <li>No production code injects {@code JdbcClient}/{@code JdbcTemplate} or references a {@code
 *       Jdbc*Repository}.
 *   <li>No PostgreSQL artifacts remain (driver, Flyway dialect, profile, migrations, IT).
 *   <li>SQLite and MySQL share exactly one mapper set — no dialect-specific mappers or XML.
 *   <li>No mapper XML uses forbidden dialect-specific upsert/returning syntax.
 * </ul>
 */
class MyBatisMigrationBoundaryTest {

  private static final java.util.List<Path> MAIN_JAVA =
      java.util.List.of(
          Path.of("datastoria-common/src/main/java"),
          Path.of("datastoria-dao/src/main/java"),
          Path.of("datastoria-service/src/main/java"),
          Path.of("datastoria-agent/src/main/java"),
          Path.of("datastoria-controller/src/main/java"),
          Path.of("datastoria-boot/src/main/java"));
  private static final Path MAPPER_XML = Path.of("datastoria-dao/src/main/resources/mapper");
  private static final Path MAPPER_JAVA =
      Path.of("datastoria-dao/src/main/java/io/github/ccweixiao/datastoria/dao/persistence/mapper");

  @Test
  void productionCodeHasNoJdbcClientOrJdbcRepositoryReferences() throws IOException {
    Pattern forbidden =
        Pattern.compile("(JdbcClient|JdbcTemplate|NamedParameterJdbcTemplate|Jdbc\\w*Repository)");
    try (Stream<Path> files = MAIN_JAVA.stream().flatMap(MyBatisMigrationBoundaryTest::javaFiles)) {
      files.forEach(
          file -> {
            String content = read(file);
            assertThat(forbidden.matcher(content).find())
                .as("production source %s must not reference JDBC client/template APIs", file)
                .isFalse();
          });
    }
  }

  @Test
  void noPostgresqlArtifactsRemain() throws IOException {
    assertThat(Path.of("datastoria-dao/src/main/resources/db/migration/postgresql")).doesNotExist();
    assertThat(Path.of("datastoria-boot/src/main/resources/application-postgres.yaml"))
        .doesNotExist();
    assertThat(Path.of("datastoria-boot/src/test/resources/application-postgres-it.yaml"))
        .doesNotExist();
    assertThat(
            Path.of(
                "datastoria-boot/src/test/java/io/github/ccweixiao/datastoria/boot/PostgresRepositoryIT.java"))
        .doesNotExist();

    String pom = read(Path.of("pom.xml"));
    assertThat(pom).doesNotContain("flyway-database-postgresql");
    assertThat(pom).doesNotContain("<artifactId>postgresql</artifactId>");
    assertThat(pom).doesNotContain("postgres-it");
  }

  @Test
  void sqliteAndMysqlShareExactlyOneMapperSet() throws IOException {
    assertThat(MAPPER_JAVA).isNotEmptyDirectory();
    assertThat(MAPPER_XML).isNotEmptyDirectory();

    Pattern dialectInName = Pattern.compile("(?i)(sqlite|mysql|postgres)");
    try (Stream<Path> files = Stream.concat(javaFiles(MAPPER_JAVA), xmlFiles(MAPPER_XML))) {
      files.forEach(
          file -> {
            String name = file.getFileName().toString();
            assertThat(dialectInName.matcher(name).find())
                .as("mapper %s must not be dialect-specific (one shared set)", name)
                .isFalse();
          });
    }
  }

  @Test
  void mapperXmlContainsNoForbiddenDialectSyntax() throws IOException {
    // The migration uses dialect-neutral UPDATE-then-INSERT upserts; no vendor-specific clauses.
    Pattern forbidden =
        Pattern.compile(
            "(?i)(ON\\s+CONFLICT|INSERT\\s+OR\\s+REPLACE|ON\\s+DUPLICATE\\s+KEY\\s+UPDATE|RETURNING|LAST_INSERT_ID)");
    try (Stream<Path> files = xmlFiles(MAPPER_XML)) {
      files.forEach(
          file -> {
            String content = read(file);
            assertThat(forbidden.matcher(content).find())
                .as("mapper XML %s must not use forbidden dialect-specific SQL", file)
                .isFalse();
          });
    }
  }

  private static Stream<Path> javaFiles(Path root) {
    try {
      return Files.walk(root).filter(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static Stream<Path> xmlFiles(Path root) throws IOException {
    return Files.walk(root).filter(p -> p.toString().endsWith(".xml"));
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }
}
