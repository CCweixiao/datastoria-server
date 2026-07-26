package io.datastoria.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FrontendMigrationBoundaryTest {

  private static final Path FRONTEND = Path.of("frontend");
  private static final Set<String> FORBIDDEN_NODE_BACKEND_DEPENDENCIES =
      Set.of(
          "@ai-sdk/anthropic",
          "@ai-sdk/cerebras",
          "@ai-sdk/google",
          "@ai-sdk/groq",
          "@ai-sdk/openai",
          "@ai-sdk/openai-compatible",
          "@ai-sdk/react",
          "@auth/core",
          "@openrouter/ai-sdk-provider",
          "@opeoginni/github-copilot-openai-compatible",
          "ai",
          "better-sqlite3",
          "jose",
          "knex",
          "mysql2",
          "next-auth",
          "pg",
          "server-only");

  @Test
  void frontendContainsNoNextApiHandlers() throws IOException {
    assertThat(regularFiles(FRONTEND.resolve("src/app/api"))).isEmpty();
  }

  @Test
  void frontendContainsNoLegacyDatabaseOrSkillRuntimeCopies() throws IOException {
    assertThat(regularFiles(FRONTEND.resolve("resources/database"))).isEmpty();
    assertThat(regularFiles(FRONTEND.resolve("resources/skills"))).isEmpty();
    assertThat(regularFiles(Path.of("src/main/resources/skills"))).isNotEmpty();
  }

  @Test
  void frontendDoesNotDeclareNodeBackendRuntimeDependencies() throws IOException {
    JsonNode packageJson =
        new ObjectMapper().readTree(Files.readString(FRONTEND.resolve("package.json")));
    JsonNode dependencies = packageJson.path("dependencies");

    assertThat(FORBIDDEN_NODE_BACKEND_DEPENDENCIES)
        .withFailMessage(
            "Node backend dependencies must not return to the browser package: %s",
            FORBIDDEN_NODE_BACKEND_DEPENDENCIES)
        .noneMatch(dependencies::has);
  }

  private static Set<Path> regularFiles(Path root) throws IOException {
    if (Files.notExists(root)) {
      return Set.of();
    }
    try (Stream<Path> paths = Files.walk(root)) {
      return paths.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toSet());
    }
  }
}
