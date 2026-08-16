package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorySourceTest {

  @TempDir Path dir;

  @Test
  void existingDirectoryWinsWithoutCloning() throws Exception {
    Path repo = dir.resolve("repo");
    Files.createDirectories(repo);
    AtomicInteger clones = new AtomicInteger();
    RepositorySource source =
        RepositorySource.of(
            repo,
            "https://example.test/repo.git",
            (r, t) -> {
              clones.incrementAndGet();
              Files.createDirectories(t);
            });

    assertThat(source.ensureReady().root()).isEqualTo(repo.toRealPath());
    assertThat(clones.get()).isZero();
  }

  @Test
  void missingDirectoryIsClonedOnceThenReady() throws Exception {
    AtomicInteger clones = new AtomicInteger();
    RepositorySource source =
        RepositorySource.of(
            dir.resolve("repo"),
            "https://example.test/repo.git",
            (r, t) -> {
              clones.incrementAndGet();
              Files.createDirectories(t);
            });

    assertThat(source.ensureReady().ok()).isTrue(); // cloner created the directory
    assertThat(clones.get()).isEqualTo(1);
    assertThat(source.ensureReady().ok()).isTrue();
    assertThat(clones.get()).isEqualTo(1);
  }

  @Test
  void failedCloneIsStickyAndManualRecoveryNeedsNoRestart() throws Exception {
    RepositorySource source =
        RepositorySource.of(
            dir.resolve("gone"),
            "https://example.test/repo.git",
            (r, t) -> {
              throw new IllegalStateException("connect timed out");
            });

    RepositorySource.EnsureOutcome first = source.ensureReady();
    assertThat(first.ok()).isFalse();
    assertThat(first.error()).contains("connect timed out");
    assertThat(first.remediation()).contains("git clone --depth 1");

    // Sticky: the second call fails fast without re-invoking the cloner.
    RepositorySource.EnsureOutcome second = source.ensureReady();
    assertThat(second.ok()).isFalse();
    assertThat(second.error()).contains("failed");

    // Operator clones manually -> the directory wins over the sticky failure.
    Files.createDirectories(dir.resolve("gone"));
    assertThat(source.ensureReady().ok()).isTrue();
  }

  @Test
  void unconfiguredRootWithoutRemoteReportsRemediation() {
    RepositorySource source = RepositorySource.of(dir.resolve("missing").toString(), "  ");

    assertThat(source.isConfigured()).isTrue(); // root configured, remote blank
    RepositorySource.EnsureOutcome outcome = source.ensureReady();
    assertThat(outcome.ok()).isFalse();
    assertThat(outcome.remediation()).contains("git clone --depth 1");
  }

  @Test
  void nothingConfiguredAtAll() {
    RepositorySource source = RepositorySource.of(null, null);
    assertThat(source.isConfigured()).isFalse();
    assertThat(source.ensureReady().error()).contains("not configured");
  }

  @Test
  void concurrentEnsureCallsTriggerExactlyOneClone() throws Exception {
    AtomicInteger clones = new AtomicInteger();
    AtomicReference<List<Thread>> waiters = new AtomicReference<>(List.of());
    RepositorySource source =
        RepositorySource.of(
            dir.resolve("race"),
            "https://example.test/repo.git",
            (r, t) -> {
              clones.incrementAndGet();
              Files.createDirectories(t);
            });
    List<Thread> threads =
        List.of(
            new Thread(source::ensureReady),
            new Thread(source::ensureReady),
            new Thread(source::ensureReady));
    threads.forEach(Thread::start);
    waiters.set(threads);
    for (Thread thread : waiters.get()) {
      thread.join();
    }
    assertThat(clones.get()).isEqualTo(1);
    assertThat(source.ensureReady().ok()).isTrue();
  }
}
