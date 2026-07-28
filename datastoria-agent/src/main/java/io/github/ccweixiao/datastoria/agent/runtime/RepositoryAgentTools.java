package io.github.ccweixiao.datastoria.agent.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Read-only repository inspection constrained to one canonical server-configured root. */
public final class RepositoryAgentTools {

  static final int MAX_SEARCH_RESULTS = 100;
  static final int MAX_READ_LINES = 400;
  static final int MAX_READ_BYTES = 100_000;
  static final long MAX_SEARCH_FILE_BYTES = 2_000_000;
  private static final Set<String> SKIPPED_DIRECTORIES =
      Set.of(".git", ".next", "node_modules", "target", "dist", "build", ".idea");

  private final Path root;
  private final ObjectMapper mapper;
  private final AgentToolExecutionPolicy executionPolicy;

  public RepositoryAgentTools(Path root) {
    this(root, new ObjectMapper(), AgentToolExecutionPolicy.untracked());
  }

  RepositoryAgentTools(Path root, ObjectMapper mapper) {
    this(root, mapper, AgentToolExecutionPolicy.untracked());
  }

  public RepositoryAgentTools(
      Path root, ObjectMapper mapper, AgentToolExecutionPolicy executionPolicy) {
    this.root = canonicalRoot(root);
    this.mapper = mapper;
    this.executionPolicy = executionPolicy;
  }

  @Tool(
      name = "search_file",
      description =
          "Search case-insensitively within the configured source repository. "
              + "Returns repo-relative files, line numbers, and bounded snippets.",
      readOnly = true)
  public Mono<String> searchFile(
      @ToolParam(name = "query", required = true, description = "Plain-text search query")
          String query,
      @ToolParam(name = "glob", required = false, description = "Optional repo-relative glob")
          String glob,
      @ToolParam(name = "limit", required = false, description = "Maximum matches, capped at 100")
          Integer limit) {
    return executionPolicy.guard(
        "search_file",
        Mono.fromCallable(() -> search(query, glob, limit))
            .subscribeOn(Schedulers.boundedElastic()));
  }

  @Tool(
      name = "read_file",
      description = "Read a bounded line range from a file under the configured source repository.",
      readOnly = true)
  public Mono<String> readFile(
      @ToolParam(name = "path", required = true, description = "Repo-relative file path")
          String path,
      @ToolParam(name = "startLine", required = false, description = "1-based inclusive start")
          Integer startLine,
      @ToolParam(name = "endLine", required = false, description = "1-based inclusive end")
          Integer endLine) {
    return executionPolicy.guard(
        "read_file",
        Mono.fromCallable(() -> read(path, startLine, endLine))
            .subscribeOn(Schedulers.boundedElastic()));
  }

  private String search(String query, String glob, Integer requestedLimit) throws IOException {
    requireConfigured();
    String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
    if (needle.isBlank()) {
      return error("query is required");
    }
    int limit =
        Math.max(1, Math.min(requestedLimit == null ? 20 : requestedLimit, MAX_SEARCH_RESULTS));
    PathMatcher matcher =
        glob == null || glob.isBlank()
            ? null
            : FileSystems.getDefault().getPathMatcher("glob:" + glob.trim());
    PathMatcher zeroDepthMatcher =
        glob != null && glob.contains("/**/")
            ? FileSystems.getDefault().getPathMatcher("glob:" + glob.trim().replace("/**/", "/"))
            : null;
    List<SearchMatch> matches = new ArrayList<>();
    boolean hasMore = false;
    List<Path> files = listSearchFiles();
    outer:
    for (Path file : files) {
      Path relative = root.relativize(file);
      if (matcher != null
          && !matcher.matches(relative)
          && (zeroDepthMatcher == null || !zeroDepthMatcher.matches(relative))) {
        continue;
      }
      if (Files.size(file) > MAX_SEARCH_FILE_BYTES || isBinary(file)) {
        continue;
      }
      List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
      for (int index = 0; index < lines.size(); index++) {
        if (!lines.get(index).toLowerCase(java.util.Locale.ROOT).contains(needle)) {
          continue;
        }
        if (matches.size() == limit) {
          hasMore = true;
          break outer;
        }
        matches.add(
            new SearchMatch(
                relative.toString().replace(java.io.File.separatorChar, '/'),
                index + 1,
                lines
                    .get(index)
                    .trim()
                    .substring(0, Math.min(300, lines.get(index).trim().length()))));
      }
    }
    if (matches.isEmpty()) {
      return error("no matches found");
    }
    ObjectNode result = mapper.createObjectNode();
    ArrayNode jsonMatches = result.putArray("matches");
    for (SearchMatch match : matches) {
      ObjectNode item = jsonMatches.addObject();
      item.put("path", match.path());
      item.put("line", match.line());
      item.put("snippet", match.snippet());
    }
    result.put("hasMore", hasMore);
    return result.toString();
  }

  private String read(String requestedPath, Integer requestedStart, Integer requestedEnd)
      throws IOException {
    requireConfigured();
    Path file = resolveFile(requestedPath);
    if (!Files.isRegularFile(file)) {
      return error("file not found");
    }
    if (Files.size(file) > MAX_SEARCH_FILE_BYTES) {
      return error("file exceeds the repository read limit");
    }
    if (isBinary(file)) {
      return error("binary file rejected");
    }
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    int start = Math.max(1, requestedStart == null ? 1 : requestedStart);
    int requestedLast =
        requestedEnd == null ? start + MAX_READ_LINES - 1 : Math.max(start, requestedEnd);
    int end = Math.min(lines.size(), Math.min(requestedLast, start + MAX_READ_LINES - 1));
    StringBuilder content = new StringBuilder();
    int emittedBytes = 0;
    int emittedEnd = start - 1;
    for (int line = start; line <= end; line++) {
      String candidate = lines.get(line - 1);
      int additional = candidate.getBytes(StandardCharsets.UTF_8).length + 1;
      if (emittedBytes + additional > MAX_READ_BYTES) {
        break;
      }
      if (!content.isEmpty()) {
        content.append('\n');
      }
      content.append(candidate);
      emittedBytes += additional;
      emittedEnd = line;
    }
    ObjectNode result = mapper.createObjectNode();
    result.put("path", root.relativize(file).toString().replace(java.io.File.separatorChar, '/'));
    result.put("startLine", start);
    result.put("endLine", emittedEnd);
    result.put("totalLines", lines.size());
    result.put("content", content.toString());
    int desiredEnd = Math.min(requestedLast, lines.size());
    result.put(
        "truncated",
        emittedEnd < desiredEnd
            || (requestedLast > start + MAX_READ_LINES - 1
                && lines.size() > start + MAX_READ_LINES - 1));
    result.put("hasPrevious", start > 1);
    result.put("hasNext", emittedEnd < lines.size());
    return result.toString();
  }

  private Path resolveFile(String value) throws IOException {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("path is required");
    }
    Path relative = Path.of(value);
    if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
      throw new IllegalArgumentException("path must stay within the configured repository");
    }
    Path candidate = root.resolve(relative).normalize();
    if (!candidate.startsWith(root)) {
      throw new IllegalArgumentException("path must stay within the configured repository");
    }
    if (!Files.exists(candidate)) {
      return candidate;
    }
    Path real = candidate.toRealPath();
    if (!real.startsWith(root)) {
      throw new IllegalArgumentException("symlink escapes the configured repository");
    }
    return real;
  }

  private List<Path> listSearchFiles() throws IOException {
    List<Path> files = new ArrayList<>();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
            if (!directory.equals(root)
                && SKIPPED_DIRECTORIES.contains(directory.getFileName().toString())) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (attrs.isRegularFile()) {
              try {
                if (file.toRealPath().startsWith(root)) {
                  files.add(file);
                }
              } catch (IOException ignored) {
                // Raced with deletion or an unreadable symlink; omit it.
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
    files.sort(Comparator.comparing(path -> root.relativize(path).toString()));
    return files;
  }

  private static boolean isBinary(Path file) {
    try {
      byte[] sample = Files.readAllBytes(file);
      int length = Math.min(sample.length, 8_192);
      for (int index = 0; index < length; index++) {
        if (sample[index] == 0) {
          return true;
        }
      }
      return false;
    } catch (IOException error) {
      return true;
    }
  }

  private String error(String message) {
    return mapper.createObjectNode().put("error", message).toString();
  }

  private void requireConfigured() {
    if (root == null) {
      throw new IllegalStateException("Repository inspection is not configured");
    }
  }

  private static Path canonicalRoot(Path configured) {
    if (configured == null) {
      return null;
    }
    try {
      Path real = configured.toRealPath();
      if (!Files.isDirectory(real) || !Files.isReadable(real)) {
        throw new IllegalArgumentException("Repository root must be a readable directory");
      }
      return real;
    } catch (IOException error) {
      throw new IllegalArgumentException("Repository root is invalid", error);
    }
  }

  private record SearchMatch(String path, int line, String snippet) {}
}
