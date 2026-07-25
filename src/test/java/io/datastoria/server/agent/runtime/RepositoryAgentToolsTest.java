package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RepositoryAgentToolsTest {

  @TempDir Path root;

  @Test
  void searchesAndReadsOnlyBoundedRepositoryRelativeContent() throws Exception {
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/app.ts"), "first\nconst ImportantValue = 42;\nlast\n");
    RepositoryAgentTools tools = new RepositoryAgentTools(root);
    ObjectMapper mapper = new ObjectMapper();

    JsonNode search =
        mapper.readTree(tools.searchFile("importantvalue", "src/**/*.ts", 10).block());
    assertThat(search.path("matches").path(0).path("path").asText()).isEqualTo("src/app.ts");
    assertThat(search.path("matches").path(0).path("line").asInt()).isEqualTo(2);
    assertThat(search.path("hasMore").asBoolean()).isFalse();

    JsonNode read = mapper.readTree(tools.readFile("src/app.ts", 2, 2).block());
    assertThat(read.path("content").asText()).isEqualTo("const ImportantValue = 42;");
    assertThat(read.path("startLine").asInt()).isEqualTo(2);
    assertThat(read.path("endLine").asInt()).isEqualTo(2);
    assertThat(read.path("hasPrevious").asBoolean()).isTrue();
  }

  @Test
  void rejectsTraversalAbsolutePathsAndEscapingSymlinks() throws Exception {
    RepositoryAgentTools tools = new RepositoryAgentTools(root);
    Path outside = Files.createTempFile("datastoria-outside-", ".txt");
    Files.writeString(outside, "secret");
    Files.createSymbolicLink(root.resolve("escape.txt"), outside);

    assertThatThrownBy(() -> tools.readFile("../secret", null, null).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configured repository");
    assertThatThrownBy(() -> tools.readFile(outside.toString(), null, null).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configured repository");
    assertThatThrownBy(() -> tools.readFile("escape.txt", null, null).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("symlink escapes");
  }
}
