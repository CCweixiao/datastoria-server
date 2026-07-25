package io.datastoria.server.agent.application;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import io.datastoria.server.agent.runtime.AgentToolRegistry;
import io.datastoria.server.agent.runtime.CancellationRegistry;
import io.datastoria.server.agent.runtime.HarnessAgentFactory;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.repository.AgentRunRepository;

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
  HarnessAgentFactory harnessAgentFactory(AgentToolRegistry toolRegistry) {
    return new HarnessAgentFactory(toolRegistry);
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
      io.datastoria.server.repository.ChatMessageRepository messageRepository,
      TransactionTemplate transactions,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    return new RunLifecycleRecorder(runRepository, messageRepository, transactions, jdbcScheduler);
  }
}
