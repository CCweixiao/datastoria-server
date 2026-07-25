package io.datastoria.server.agent.application;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.runtime.CancellationRegistry;
import io.datastoria.server.agent.runtime.HarnessAgentFactory;
import io.datastoria.server.agent.runtime.RunnableAgent;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Application skeleton that orchestrates a run: builds a minimal-permission HarnessAgent via {@link
 * HarnessAgentFactory}, maps its events to {@link AgentRunEvent}s (errors consumed into {@code
 * RunFailed}), and registers it for cancellation. P4.2 delivers this skeleton; P4.6 adds HTTP
 * transport, idempotency, session/message persistence, and credential injection.
 *
 * <p><b>Threading:</b> the returned {@link Flux} is fully reactive. The only blocking AgentScope
 * call is {@code RunnableAgent.close()} at termination, which is dispatched to a dedicated bounded
 * executor (default: a single-thread daemon) so it never blocks the Netty event loop
 * (docs/delivery/ai-implementation-playbook.md §2). Tests inject a synchronous executor for
 * deterministic cleanup.
 */
public final class AgentRunService {

  private final HarnessAgentFactory factory;
  private final CancellationRegistry registry;
  private final Executor cleanupExecutor;

  public AgentRunService(HarnessAgentFactory factory, CancellationRegistry registry) {
    this(
        factory,
        registry,
        Executors.newSingleThreadExecutor(
            r -> {
              Thread thread = new Thread(r, "agent-run-cleanup");
              thread.setDaemon(true);
              return thread;
            }));
  }

  public AgentRunService(
      HarnessAgentFactory factory, CancellationRegistry registry, Executor cleanupExecutor) {
    this.factory = factory;
    this.registry = registry;
    this.cleanupExecutor = cleanupExecutor;
  }

  /**
   * Starts a run and returns its mapped event stream. The caller subscribes (P4.6 controller /
   * tests); on subscribe it should bind the resulting {@link Disposable} via {@link
   * #bindSubscription} so the run can be cancelled by id. Client disconnect simply disposes the
   * subscription — that propagates upstream and stops the provider, with no registry call needed.
   */
  public Flux<AgentRunEvent> start(RunRequest request) {
    RunnableAgent agent =
        factory.create(
            request.context(), request.modelAdapter(), request.config(), request.userText());
    String runId = request.context().runId();
    registry.register(request.context(), agent);
    return agent
        .streamEvents()
        .doFinally(
            signal -> {
              registry.unregister(runId);
              cleanupExecutor.execute(
                  () -> {
                    try {
                      agent.close();
                    } catch (Exception ignored) {
                      // Cleanup must never mask the terminal signal.
                    }
                  });
            });
  }

  /**
   * Server-initiated cancel. Succeeds only for the run owner; disposes the bound subscription and
   * cooperatively interrupts the agent.
   */
  public boolean cancel(String runId, String tenantId, String userId) {
    return registry.cancel(runId, tenantId, userId);
  }

  /** Binds a live subscription so the run can be cancelled by id (see {@link #start}). */
  public void bindSubscription(String runId, Disposable subscription) {
    registry.bindSubscription(runId, subscription);
  }

  public boolean isActive(String runId) {
    return registry.isActive(runId);
  }
}
