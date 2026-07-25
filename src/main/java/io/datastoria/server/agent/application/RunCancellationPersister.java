package io.datastoria.server.agent.application;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.repository.AgentRunRepository;

/**
 * The independent run-lifecycle observer that persists {@link AgentRunStatus#CANCELLED}. Wired as
 * {@link io.datastoria.server.agent.runtime.CancellationRegistry}'s counterpart in {@link
 * AgentRunService}: when a run's Reactor subscription is cancelled (client disconnect or server
 * cancel), {@code AgentRunService} emits a {@link AgentRunEvent.RunCancelled} to this observer,
 * which transitions the run row to {@code cancelled}.
 *
 * <p><b>Threading:</b> the observer is invoked synchronously inside {@code AgentRunService}'s
 * {@code doFinally} (typically a Netty event-loop thread). The blocking JDBC write is therefore
 * dispatched to a dedicated bounded executor so it never blocks the event loop. Tests inject a
 * synchronous executor for deterministic behavior.
 *
 * <p><b>No flux interaction:</b> this observer only writes to the database; it never tries to push
 * a {@code RunCancelled} back into an already-cancelled stream. The transition is idempotent (a run
 * already cancelled is a no-op success).
 */
public final class RunCancellationPersister implements Consumer<AgentRunEvent.RunCancelled> {

  private static final Logger log = LoggerFactory.getLogger(RunCancellationPersister.class);

  private final AgentRunRepository runRepository;
  private final Executor executor;

  public RunCancellationPersister(AgentRunRepository runRepository) {
    this(runRepository, defaultExecutor());
  }

  public RunCancellationPersister(AgentRunRepository runRepository, Executor executor) {
    this.runRepository = runRepository;
    this.executor = executor;
  }

  @Override
  public void accept(AgentRunEvent.RunCancelled event) {
    executor.execute(
        () -> {
          try {
            runRepository.applyCancellation(event.runId(), event.occurredAt());
          } catch (Exception e) {
            // This path only carries run id + status (no prompt, key, or credential); log the
            // exception class so ops can see failures without echoing SQL or parameter text.
            log.warn(
                "Failed to persist cancellation for run {}: {}",
                event.runId(),
                e.getClass().getName());
          }
        });
  }

  private static Executor defaultExecutor() {
    return Executors.newSingleThreadExecutor(
        r -> {
          Thread thread = new Thread(r, "agent-run-cancel-persist");
          thread.setDaemon(true);
          return thread;
        });
  }
}
