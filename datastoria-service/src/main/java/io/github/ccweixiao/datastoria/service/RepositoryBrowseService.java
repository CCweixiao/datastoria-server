package io.github.ccweixiao.datastoria.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;

/** Bounded read-only source browser used by the UI code viewer. */
@Service
public class RepositoryBrowseService {

  private static final int MAX_LINES = 2_000;
  private static final int MAX_BYTES = 256 * 1_024;
  private static final int MAX_SOURCE_FILE_BYTES = 2_000_000;
  private static final Set<String> SKIPPED =
      Set.of(".git", ".next", ".local", "node_modules", "target", "dist", "build", ".idea");
  private static final Set<String> BROWSEABLE_SUFFIXES =
      Set.of(
          ".c",
          ".cc",
          ".cpp",
          ".cxx",
          ".h",
          ".hh",
          ".hpp",
          ".hxx",
          ".java",
          ".kt",
          ".ts",
          ".tsx",
          ".js",
          ".jsx",
          ".mjs",
          ".json",
          ".yaml",
          ".yml",
          ".xml",
          ".properties",
          ".md",
          ".sql",
          ".sh",
          ".css",
          ".html",
          ".toml",
          ".gradle");

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
          .filter(RepositoryBrowseService::isBrowseable)
          .map(root::relativize)
          .map(path -> path.toString().replace(java.io.File.separatorChar, '/'))
          .sorted()
          .limit(10_000)
          .toList();
    }
  }

  private static boolean isBrowseable(Path path) {
    String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
    return BROWSEABLE_SUFFIXES.stream().anyMatch(name::endsWith);
  }

  public FileView read(String relativePath, Integer requestedStart, Integer requestedEnd)
      throws IOException {
    Path file = resolve(relativePath);
    if (Files.size(file) > MAX_SOURCE_FILE_BYTES) {
      throw PlainTextException.badRequest(ApiErrorCode.REPOSITORY_FILE_TOO_LARGE);
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
          false,
          false);
    }
    int start = Math.max(1, requestedStart == null ? 1 : requestedStart);
    int end =
        Math.min(
            total, requestedEnd == null ? Math.min(total, start + MAX_LINES - 1) : requestedEnd);
    if (end < start) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_FILE_RANGE);
    }
    end = Math.min(end, start + MAX_LINES - 1);
    StringBuilder content = new StringBuilder();
    int usedBytes = 0;
    boolean truncated = false;
    for (int lineIndex = start - 1; lineIndex < end; lineIndex++) {
      String next = (lineIndex == start - 1 ? "" : "\n") + lines.get(lineIndex);
      byte[] bytes = next.getBytes(StandardCharsets.UTF_8);
      if (usedBytes + bytes.length > MAX_BYTES) {
        usedBytes += appendWithinUtf8Limit(content, next, MAX_BYTES - usedBytes);
        truncated = true;
        break;
      }
      content.append(next);
      usedBytes += bytes.length;
    }
    return new FileView(
        root.relativize(file).toString().replace(java.io.File.separatorChar, '/'),
        content.toString(),
        start,
        end,
        total,
        start > 1,
        end < total,
        truncated);
  }

  private static int appendWithinUtf8Limit(
      StringBuilder destination, String value, int remainingBytes) {
    int appendedBytes = 0;
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      String character = new String(Character.toChars(codePoint));
      int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
      if (appendedBytes + characterBytes > remainingBytes) {
        break;
      }
      destination.append(character);
      appendedBytes += characterBytes;
      offset += Character.charCount(codePoint);
    }
    return appendedBytes;
  }

  private Path resolve(String relativePath) throws IOException {
    if (relativePath == null || relativePath.isBlank() || Path.of(relativePath).isAbsolute()) {
      throw PlainTextException.badRequest(ApiErrorCode.REPOSITORY_PATH_REQUIRED);
    }
    Path candidate = root.resolve(relativePath).normalize();
    if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
      throw new NotFoundException("RepositoryFile", relativePath);
    }
    Path real = candidate.toRealPath();
    if (!real.startsWith(root)) {
      throw PlainTextException.badRequest(ApiErrorCode.REPOSITORY_PATH_OUTSIDE_ROOT);
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
      boolean hasNext,
      boolean truncated) {}
}
