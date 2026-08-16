package io.github.ccweixiao.datastoria.agent.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server-configured source repository behind {@code search_file} / {@code read_file}, with lazy
 * materialization: when the configured root directory does not exist and a remote is configured,
 * the first tool use shallow-clones the repository once (10-minute budget, temp-dir + atomic
 * rename, same layout the original Next.js implementation used). Failures are sticky for the
 * process lifetime but always yield to a directory the operator creates manually — no restart is
 * needed to recover.
 *
 * <p>This class is deliberately dependency-free so the agent runtime can use it without Spring.
 */
public final class RepositorySource {

  private static final Logger log = LoggerFactory.getLogger(RepositorySource.class);

  /** Fixed shallow-clone budget; a full ClickHouse checkout over a slow link fits comfortably. */
  static final Duration CLONE_TIMEOUT = Duration.ofMinutes(10);

  /** Performs the actual clone; injectable for tests. */
  @FunctionalInterface
  public interface CloneOperator {
    void clone(String remote, Path target) throws Exception;
  }

  record EnsureOutcome(Path root, String error, String remediation) {
    boolean ok() {
      return root != null;
    }
  }

  private final Path configuredRoot;
  private final String remote;
  private final CloneOperator cloner;
  private final AtomicLong cloneAttempts = new AtomicLong();
  private volatile String lastFailure;

  private RepositorySource(Path configuredRoot, String remote, CloneOperator cloner) {
    this.configuredRoot = configuredRoot;
    this.remote = normalizedRemote(remote);
    this.cloner = cloner;
  }

  /** A repository that already exists locally; never clones. */
  public static RepositorySource localOnly(Path root) {
    return new RepositorySource(root, null, null);
  }

  /**
   * A repository rooted at {@code root} (nullable when unconfigured) that shallow-clones {@code
   * remote} into place on first use when the directory is missing.
   */
  public static RepositorySource of(String root, String remote) {
    return new RepositorySource(
        root == null || root.isBlank() ? null : Path.of(root.trim()),
        remote,
        GitShallowClone.INSTANCE);
  }

  /** Visible for tests. */
  static RepositorySource of(Path root, String remote, CloneOperator cloner) {
    return new RepositorySource(root, remote, cloner);
  }

  /** True when neither a local root nor a remote is configured (tools report disabled). */
  public boolean isConfigured() {
    return configuredRoot != null || remote != null;
  }

  /**
   * Returns the ready repository root, or the failure the tool should surface to the agent (which
   * relays it to the user in the conversation). Blocking; callers run on boundedElastic.
   */
  public synchronized EnsureOutcome ensureReady() {
    if (configuredRoot == null) {
      return new EnsureOutcome(
          null,
          "Repository inspection is not configured",
          "Set DATASTORIA_AGENT_REPOSITORY_ROOT to a local source checkout.");
    }
    Path canonical = existingDirectory(configuredRoot);
    if (canonical != null) {
      if (lastFailure != null) {
        log.info(
            "Source repository became available at {} (manual recovery); resuming repository"
                + " inspection",
            canonical);
        lastFailure = null;
      } else {
        log.debug("Repository inspection root ready at {}", canonical);
      }
      // An operator-created directory always wins, even after a failed clone attempt.
      return new EnsureOutcome(canonical, null, null);
    }
    if (remote == null) {
      return new EnsureOutcome(
          null,
          "The configured source directory does not exist: " + configuredRoot,
          "Clone the source manually (git clone --depth 1 <repo> "
              + configuredRoot
              + ") or fix DATASTORIA_AGENT_REPOSITORY_ROOT.");
    }
    if (lastFailure != null && cloneAttempts.get() > 0) {
      // One automatic attempt per process; repeated 10-minute blocking retries would stall runs.
      log.debug("Skipping source clone retry; previous attempt failed: {}", lastFailure);
      return new EnsureOutcome(null, lastFailure, manualRemediation());
    }
    cloneAttempts.incrementAndGet();
    log.info(
        "Source repository {} is missing; shallow-cloning {} into it (up to {} minutes, tools"
            + " wait meanwhile)",
        configuredRoot,
        remote,
        CLONE_TIMEOUT.toMinutes());
    long startedAt = System.nanoTime();
    try {
      cloner.clone(remote, configuredRoot);
    } catch (Exception error) {
      lastFailure = "Automatic source checkout failed (" + summary(error) + ")";
      log.warn(
          "Automatic source checkout failed after {}s: {}",
          elapsedSeconds(startedAt),
          summary(error));
      return new EnsureOutcome(null, lastFailure, manualRemediation());
    }
    canonical = existingDirectory(configuredRoot);
    if (canonical == null) {
      lastFailure = "Automatic source checkout finished but the directory is missing";
      log.warn("Automatic source checkout finished but {} is still missing", configuredRoot);
      return new EnsureOutcome(null, lastFailure, manualRemediation());
    }
    lastFailure = null;
    log.info(
        "Source repository materialized at {} in {}s; repository inspection is now available",
        canonical,
        elapsedSeconds(startedAt));
    return new EnsureOutcome(canonical, null, null);
  }

  private static long elapsedSeconds(long startedAtNanos) {
    return Math.max(1, (System.nanoTime() - startedAtNanos) / 1_000_000_000L);
  }

  private String manualRemediation() {
    return "Run it manually and the tools will pick it up without a restart: git clone --depth 1 "
        + remote
        + " "
        + configuredRoot
        + " (or point DATASTORIA_AGENT_REPOSITORY_ROOT at an existing checkout).";
  }

  private static String summary(Exception error) {
    String message = error.getMessage();
    String type = error.getClass().getSimpleName();
    return message == null || message.isBlank()
        ? type
        : type + ": " + message.substring(0, Math.min(message.length(), 300));
  }

  private static String normalizedRemote(String remote) {
    if (remote == null) {
      return null;
    }
    String trimmed = remote.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static Path existingDirectory(Path configured) {
    try {
      Path real = configured.toRealPath();
      return Files.isDirectory(real) && Files.isReadable(real) ? real : null;
    } catch (IOException error) {
      return null;
    }
  }

  /** Default shallow cloner: {@code git clone --depth 1 --single-branch} + atomic rename. */
  static final class GitShallowClone implements CloneOperator {

    static final GitShallowClone INSTANCE = new GitShallowClone();

    private GitShallowClone() {}

    @Override
    public void clone(String remote, Path target) throws Exception {
      Path parent = target.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      String stamp =
          String.format(
              Locale.ROOT, "%d-%d", ProcessHandle.current().pid(), System.currentTimeMillis());
      Path temp = target.resolveSibling(target.getFileName() + ".tmp-" + stamp);
      // git writes errors (never progress — stderr is not a tty) into this file; the tail is
      // folded into the failure message so operators see git's own reason in the log.
      Path errorFile = target.resolveSibling(target.getFileName() + ".err-" + stamp);
      Process process = null;
      try {
        process =
            new ProcessBuilder(
                    "git", "clone", "--depth", "1", "--single-branch", remote, temp.toString())
                // Discard stdout and file-redirect stderr: a piped stream nobody drains would
                // fill the OS pipe buffer and deadlock the clone.
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.to(errorFile.toFile()))
                .start();
        if (!process.waitFor(
            CLONE_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
          process.destroyForcibly();
          process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
          throw new IllegalStateException("git clone timed out: " + errorTail(errorFile));
        }
        if (process.exitValue() != 0) {
          throw new IllegalStateException(
              "git clone exited with " + process.exitValue() + ": " + errorTail(errorFile));
        }
        Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
      } finally {
        if (process != null && process.isAlive()) {
          process.destroyForcibly();
        }
        deleteRecursively(temp);
        java.nio.file.Files.deleteIfExists(errorFile);
      }
    }

    /** Last line of git's stderr (bounded), so the log shows git's own failure reason. */
    private static String errorTail(Path errorFile) {
      try {
        List<String> lines = Files.readAllLines(errorFile, StandardCharsets.UTF_8);
        for (int index = lines.size() - 1; index >= 0; index--) {
          String line = lines.get(index).trim();
          if (!line.isEmpty()) {
            return line.length() > 300 ? line.substring(0, 300) : line;
          }
        }
      } catch (IOException ignored) {
        // No error output or unreadable file; the exit reason alone is still logged.
      }
      return "no git error output";
    }

    private static void deleteRecursively(Path path) {
      try {
        Files.walk(path)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(p -> p.toFile().delete());
      } catch (IOException ignored) {
        // Best effort cleanup of the temp clone.
      }
    }
  }
}
