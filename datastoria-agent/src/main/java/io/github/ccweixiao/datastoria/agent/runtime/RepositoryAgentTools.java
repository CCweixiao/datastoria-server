package io.github.ccweixiao.datastoria.agent.runtime;

import java.io.BufferedReader;
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
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Read-only repository inspection constrained to one server-configured {@link RepositorySource}.
 *
 * <p>Robustness contract (aligned with the original Next.js implementation and hardened for the
 * AgentScope runtime): every failure — repository not configured, materialization failure, missing
 * files, oversized or binary files — surfaces as a structured JSON error for the model to relay in
 * the conversation, never as an exception that breaks the agent loop. Searches stream files
 * line-by-line (no full-file reads), skip non-source suffixes and binaries after an 8KB sniff, are
 * bounded by a soft 60-second deadline (partial results plus a {@code truncated} flag instead of an
 * unbounded walk), and reuse a short-lived file listing so repeated queries on a full ClickHouse
 * checkout do not re-walk the tree each time.
 */
public final class RepositoryAgentTools {

  static final int MAX_SEARCH_RESULTS = 100;
  static final int MAX_READ_LINES = 400;
  static final int MAX_READ_BYTES = 100_000;
  static final long MAX_SEARCH_FILE_BYTES = 2_000_000;
  private static final long SEARCH_DEADLINE_NANOS = 60_000_000_000L;
  private static final long LISTING_CACHE_NANOS = 60_000_000_000L;
  private static final int BINARY_SNIFF_BYTES = 8_192;
  private static final Set<String> SKIPPED_DIRECTORIES =
      Set.of(".git", ".next", "node_modules", "target", "dist", "build", ".idea");
  private static final Set<String> SEARCHABLE_SUFFIXES =
      Set.of(
          ".c",
          ".cc",
          ".cpp",
          ".cxx",
          ".h",
          ".hh",
          ".hpp",
          ".hxx",
          ".inl",
          ".ipp",
          ".java",
          ".kt",
          ".kts",
          ".py",
          ".go",
          ".rs",
          ".rb",
          ".sql",
          ".proto",
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
          ".sh",
          ".cmake",
          ".toml",
          ".gradle");

  private final RepositorySource source;
  private final ObjectMapper mapper;
  private final AgentToolExecutionPolicy executionPolicy;
  private final Object listingLock = new Object();
  private volatile List<Path> cachedListing;
  private volatile Path cachedListingRoot;
  private volatile long cachedListingAt;

  public RepositoryAgentTools(Path root) {
    this(
        RepositorySource.localOnly(root), new ObjectMapper(), AgentToolExecutionPolicy.untracked());
  }

  RepositoryAgentTools(Path root, ObjectMapper mapper) {
    this(RepositorySource.localOnly(root), mapper, AgentToolExecutionPolicy.untracked());
  }

  public RepositoryAgentTools(
      RepositorySource source, ObjectMapper mapper, AgentToolExecutionPolicy executionPolicy) {
    this.source = source;
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
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (needle.isBlank()) {
      return error("query is required");
    }
    RepositorySource.EnsureOutcome ready = source.ensureReady();
    if (!ready.ok()) {
      return notReady(ready);
    }
    Path root = ready.root();
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
    List<Path> files = listing(root);
    List<SearchMatch> matches = new ArrayList<>();
    boolean hasMore = false;
    boolean deadlineHit = false;
    long deadline = System.nanoTime() + SEARCH_DEADLINE_NANOS;
    outer:
    for (Path file : files) {
      if (System.nanoTime() > deadline) {
        deadlineHit = true;
        break;
      }
      Path relative = root.relativize(file);
      if (matcher != null
          && !matcher.matches(relative)
          && (zeroDepthMatcher == null || !zeroDepthMatcher.matches(relative))) {
        continue;
      }
      if (!isSearchableName(file) || Files.size(file) > MAX_SEARCH_FILE_BYTES) {
        continue;
      }
      List<SearchMatch> fileMatches = scanFile(root, file, needle, limit - matches.size());
      if (fileMatches == null) {
        continue; // binary or unreadable
      }
      for (SearchMatch match : fileMatches) {
        if (matches.size() == limit) {
          hasMore = true;
          break outer;
        }
        matches.add(match);
      }
    }
    if (matches.isEmpty()) {
      return error(
          deadlineHit
              ? "no matches found before the search time budget ran out"
              : "no matches found");
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
    if (deadlineHit) {
      result.put("truncated", true);
      result.put(
          "guidance",
          "The search stopped at its time budget with partial results; narrow the query or add a"
              + " glob to continue.");
    }
    return result.toString();
  }

  private List<SearchMatch> scanFile(Path root, Path file, String needle, int remaining) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      if (isBinary(reader)) {
        return null;
      }
      List<SearchMatch> found = new ArrayList<>();
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (!line.toLowerCase(Locale.ROOT).contains(needle)) {
          continue;
        }
        String trimmed = line.trim();
        found.add(
            new SearchMatch(
                root.relativize(file).toString().replace(java.io.File.separatorChar, '/'),
                lineNumber,
                trimmed.substring(0, Math.min(300, trimmed.length()))));
        if (found.size() >= remaining) {
          break;
        }
      }
      return found;
    } catch (IOException error) {
      // Raced with deletion or decoding issues; the file is simply skipped.
      return null;
    }
  }

  private String read(String requestedPath, Integer requestedStart, Integer requestedEnd)
      throws IOException {
    RepositorySource.EnsureOutcome ready = source.ensureReady();
    if (!ready.ok()) {
      return notReady(ready);
    }
    Path root = ready.root();
    Path file = resolveFile(root, requestedPath);
    if (!Files.isRegularFile(file)) {
      return error("file not found");
    }
    if (Files.size(file) > MAX_SEARCH_FILE_BYTES) {
      return error("file exceeds the repository read limit");
    }
    List<String> lines = readBoundedLines(file);
    if (lines == null) {
      return error("binary file rejected");
    }
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

  private Path resolveFile(Path root, String value) {
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
    Path real;
    try {
      real = candidate.toRealPath();
    } catch (IOException error) {
      throw new IllegalArgumentException("path must stay within the configured repository");
    }
    if (!real.startsWith(root)) {
      throw new IllegalArgumentException("symlink escapes the configured repository");
    }
    return real;
  }

  /**
   * Short-lived cached listing of candidate files. A full ClickHouse checkout holds tens of
   * thousands of files; walking it per query dominated search latency.
   */
  private List<Path> listing(Path root) throws IOException {
    List<Path> cached = cachedListing;
    if (cached != null
        && root.equals(cachedListingRoot)
        && System.nanoTime() - cachedListingAt < LISTING_CACHE_NANOS) {
      return cached;
    }
    synchronized (listingLock) {
      cached = cachedListing;
      if (cached != null
          && root.equals(cachedListingRoot)
          && System.nanoTime() - cachedListingAt < LISTING_CACHE_NANOS) {
        return cached;
      }
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
              if (attrs.isRegularFile() && isSearchableName(file)) {
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
      cachedListing = files;
      cachedListingRoot = root;
      cachedListingAt = System.nanoTime();
      return files;
    }
  }

  private static boolean isSearchableName(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    return SEARCHABLE_SUFFIXES.stream().anyMatch(name::endsWith);
  }

  /** Sniffs the first bytes of the stream for NULs without reading the whole file. */
  private static boolean isBinary(BufferedReader reader) throws IOException {
    reader.mark(BINARY_SNIFF_BYTES + 1);
    char[] buffer = new char[BINARY_SNIFF_BYTES];
    int read = reader.read(buffer, 0, buffer.length);
    reader.reset();
    for (int index = 0; index < read; index++) {
      if (buffer[index] == 0) {
        return true;
      }
    }
    return false;
  }

  private static List<String> readBoundedLines(Path file) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException error) {
      return null;
    }
  }

  private String notReady(RepositorySource.EnsureOutcome outcome) {
    ObjectNode result = mapper.createObjectNode();
    result.put("error", outcome.error());
    result.put("remediation", outcome.remediation());
    result.put(
        "guidance",
        "Tell the user the source repository is unavailable and what to do; other work can"
            + " continue without repository inspection.");
    return result.toString();
  }

  private String error(String message) {
    return mapper.createObjectNode().put("error", message).toString();
  }

  private record SearchMatch(String path, int line, String snippet) {}
}
