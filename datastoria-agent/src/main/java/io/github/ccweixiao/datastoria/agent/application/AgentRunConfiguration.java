package io.github.ccweixiao.datastoria.agent.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import io.agentscope.core.shutdown.AgentScopeJvmShutdownHook;
import io.agentscope.core.shutdown.GracefulShutdownConfig;
import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.shutdown.PartialReasoningPolicy;
import io.agentscope.core.state.AgentStateStore;
import io.github.ccweixiao.datastoria.agent.runtime.AgentHarnessSettings;
import io.github.ccweixiao.datastoria.agent.runtime.AgentToolRegistry;
import io.github.ccweixiao.datastoria.agent.runtime.CancellationRegistry;
import io.github.ccweixiao.datastoria.agent.runtime.HarnessAgentFactory;
import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.dao.repository.AgentRunRepository;

import reactor.core.scheduler.Scheduler;

/**
 * Wires the agent run layer as Spring beans: the {@link HarnessAgentFactory} (only AgentScope
 * runtime), {@link CancellationRegistry}, the {@link RunCancellationPersister} as the cancellation
 * lifecycle observer, a dedicated cleanup executor (so AgentScope {@code close()} never blocks the
 * Netty event loop), and the resulting {@link AgentRunService}. {@link ChatRunService} and the
 * controller are component-scanned.
 */
@Configuration
public class AgentRunConfiguration {

  @Bean
  AgentHarnessSettings agentHarnessSettings(
      @Value("${datastoria.agent.data-dir:}") String dataDir,
      @Value("${datastoria.agent.max-iters:25}") int maxIters,
      @Value("${datastoria.agent.tool-result-eviction-chars:32768}") int evictionChars,
      @Value("${datastoria.agent.compaction.trigger-ratio:0.8}") double triggerRatio,
      @Value("${datastoria.agent.compaction.fallback-context-tokens:100000}")
          int fallbackContextTokens,
      @Value("${datastoria.agent.shutdown-timeout-seconds:20}") int shutdownTimeoutSeconds)
      throws IOException {
    Path resolved =
        dataDir == null || dataDir.isBlank()
            ? null
            : Path.of(dataDir.trim()).toAbsolutePath().normalize();
    AgentHarnessSettings settings =
        new AgentHarnessSettings(
            resolved,
            maxIters,
            evictionChars,
            triggerRatio,
            fallbackContextTokens,
            shutdownTimeoutSeconds);
    // Fail fast on an unusable data directory rather than per-run at eviction time.
    Files.createDirectories(settings.dataDir());
    return settings;
  }

  /**
   * AgentScope graceful shutdown: on SIGTERM the JVM hook stops accepting new reasoning/acting,
   * interrupts in-flight runs and waits up to the configured timeout before the JVM exits. The
   * per-agent {@code GracefulShutdownMiddleware} is attached by the {@link HarnessAgentFactory};
   * this bean only installs the process-wide hook and policy once.
   */
  @Bean
  GracefulShutdownManager gracefulShutdownManager(AgentHarnessSettings settings) {
    GracefulShutdownManager manager = GracefulShutdownManager.getInstance();
    manager.setConfig(
        new GracefulShutdownConfig(
            Duration.ofSeconds(settings.shutdownTimeoutSeconds()), PartialReasoningPolicy.SAVE));
    AgentScopeJvmShutdownHook.register(manager);
    return manager;
  }

  @Bean
  HarnessAgentFactory harnessAgentFactory(
      AgentToolRegistry toolRegistry,
      AgentStateStore stateStore,
      AgentHarnessSettings settings,
      GracefulShutdownManager shutdownManager) {
    // AgentStateStoreConfig always supplies the MySQL-backed store.
    return new HarnessAgentFactory(
        Clock.systemUTC(), toolRegistry, stateStore, settings, shutdownManager);
  }

  @Bean
  CancellationRegistry cancellationRegistry() {
    return new CancellationRegistry();
  }

  @Bean
  RunCancellationPersister runCancellationPersister(AgentRunRepository runRepository) {
    return new RunCancellationPersister(runRepository);
  }

  @Bean(destroyMethod = "shutdown")
  Executor agentRunCleanupExecutor() {
    return Executors.newSingleThreadExecutor(
        r -> {
          Thread thread = new Thread(r, "agent-run-cleanup");
          thread.setDaemon(true);
          return thread;
        });
  }

  @Bean
  AgentRunService agentRunService(
      HarnessAgentFactory factory,
      CancellationRegistry registry,
      Executor agentRunCleanupExecutor,
      RunCancellationPersister cancellationPersister) {
    return new AgentRunService(factory, registry, agentRunCleanupExecutor, cancellationPersister);
  }

  @Bean
  RunLifecycleRecorder runLifecycleRecorder(
      AgentRunRepository runRepository,
      io.github.ccweixiao.datastoria.dao.repository.ChatMessageRepository messageRepository,
      io.github.ccweixiao.datastoria.dao.repository.AgentPendingActionRepository pendingActions,
      CheckpointStore checkpoints,
      PendingActionCheckpointCodec pendingCheckpointCodec,
      com.fasterxml.jackson.databind.ObjectMapper mapper,
      TransactionTemplate transactions,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    return new RunLifecycleRecorder(
        runRepository,
        messageRepository,
        transactions,
        jdbcScheduler,
        mapper,
        pendingActions,
        checkpoints,
        pendingCheckpointCodec);
  }
}
