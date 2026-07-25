package io.datastoria.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryBrowseServiceTest {

  @TempDir Path root;

  @Test
  void listsAndReadsBoundedRepoRelativeFiles() throws Exception {
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("src/example.ts"), "first\nsecond\nthird\n");
    Files.createDirectories(root.resolve("node_modules/pkg"));
    Files.writeString(root.resolve("node_modules/pkg/hidden.js"), "hidden");
    RepositoryBrowseService service = new RepositoryBrowseService(root.toString());

    assertThat(service.listFiles()).containsExactly("src/example.ts");
    RepositoryBrowseService.FileView view = service.read("src/example.ts", 2, 3);

    assertThat(view.content()).isEqualTo("second\nthird");
    assertThat(view.startLine()).isEqualTo(2);
    assertThat(view.endLine()).isEqualTo(3);
    assertThat(view.hasPrevious()).isTrue();
  }

  @Test
  void rejectsTraversalAndAbsolutePaths() {
    RepositoryBrowseService service = new RepositoryBrowseService(root.toString());

    assertThatThrownBy(() -> service.read("../secret", null, null))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> service.read(root.resolve("secret").toString(), null, null))
        .isInstanceOf(RuntimeException.class);
  }
}
