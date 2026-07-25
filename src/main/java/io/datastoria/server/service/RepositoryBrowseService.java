package io.datastoria.server.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.PlainTextException;

/** Bounded read-only source browser used by the UI code viewer. */
@Service
public class RepositoryBrowseService {

  private static final int MAX_LINES = 400;
  private static final int MAX_BYTES = 100_000;
  private static final int MAX_SOURCE_FILE_BYTES = 2_000_000;
  private static final Set<String> SKIPPED =
      Set.of(".git", ".next", "node_modules", "target", "dist", "build", ".idea");

  private final Path root;

  public RepositoryBrowseService(
      @Value("${datastoria.agent.repository-root:${user.dir}}") String repositoryRoot) {
    try {
      this.root = Path.of(repositoryRoot).toRealPath();
    } catch (IOException e) {
      throw new IllegalStateException("Configured repository root is unavailable");
    }
  }

  public List<String> listFiles() throws IOException {
    try (var files = Files.walk(root)) {
      return files
          .filter(Files::isRegularFile)
          .filter(path -> root.relativize(path).getNameCount() > 0)
          .filter(
              path ->
                  java.util.stream.StreamSupport.stream(root.relativize(path).spliterator(), false)
                      .noneMatch(segment -> SKIPPED.contains(segment.toString())))
          .map(root::relativize)
          .map(path -> path.toString().replace(java.io.File.separatorChar, '/'))
          .sorted()
          .limit(10_000)
          .toList();
    }
  }

  public FileView read(String relativePath, Integer requestedStart, Integer requestedEnd)
      throws IOException {
    Path file = resolve(relativePath);
    if (Files.size(file) > MAX_SOURCE_FILE_BYTES) {
      throw PlainTextException.badRequest("Repository file is too large to browse");
    }
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    int total = lines.size();
    if (total == 0) {
      return new FileView(
          root.relativize(file).toString().replace(java.io.File.separatorChar, '/'),
          "",
          1,
          0,
          0,
          false,
          false);
    }
    int start = Math.max(1, requestedStart == null ? 1 : requestedStart);
    int end =
        Math.min(
            total, requestedEnd == null ? Math.min(total, start + MAX_LINES - 1) : requestedEnd);
    if (end < start || end - start + 1 > MAX_LINES) {
      throw PlainTextException.badRequest("Requested file range is invalid or too large");
    }
    String content = String.join("\n", lines.subList(start - 1, end));
    if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw PlainTextException.badRequest("Requested file range is too large");
    }
    return new FileView(
        root.relativize(file).toString().replace(java.io.File.separatorChar, '/'),
        content,
        start,
        end,
        total,
        start > 1,
        end < total);
  }

  private Path resolve(String relativePath) throws IOException {
    if (relativePath == null || relativePath.isBlank() || Path.of(relativePath).isAbsolute()) {
      throw PlainTextException.badRequest("A repo-relative path is required");
    }
    Path candidate = root.resolve(relativePath).normalize();
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
      throw new NotFoundException("RepositoryFile", relativePath);
    }
    Path real = candidate.toRealPath();
    if (!real.startsWith(root)) {
      throw PlainTextException.badRequest("Repository path escapes the configured root");
    }
    return real;
  }

  public record FileView(
      String path,
      String content,
      int startLine,
      int endLine,
      int totalLines,
      boolean hasPrevious,
      boolean hasNext) {}
}
