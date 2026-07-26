package io.datastoria.server.agent.application;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunFailureCode;
import io.datastoria.server.agent.runtime.ApprovalResumeRequest;
import io.datastoria.server.agent.runtime.CancellationRegistry;
import io.datastoria.server.agent.runtime.HarnessAgentFactory;
import io.datastoria.server.agent.runtime.QuestionResumeRequest;
import io.datastoria.server.agent.runtime.RunnableAgent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

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
  private final Consumer<AgentRunEvent.RunCancelled> cancellationObserver;

  public AgentRunService(HarnessAgentFactory factory, CancellationRegistry registry) {
    this(
        factory,
        registry,
        Executors.newSingleThreadExecutor(
            r -> {
              Thread thread = new Thread(r, "agent-run-cleanup");
              thread.setDaemon(true);
              return thread;
            }),
        ignored -> {});
  }

  public AgentRunService(
      HarnessAgentFactory factory, CancellationRegistry registry, Executor cleanupExecutor) {
    this(factory, registry, cleanupExecutor, ignored -> {});
  }

  public AgentRunService(
      HarnessAgentFactory factory,
      CancellationRegistry registry,
      Executor cleanupExecutor,
      Consumer<AgentRunEvent.RunCancelled> cancellationObserver) {
    this.factory = factory;
    this.registry = registry;
    this.cleanupExecutor = cleanupExecutor;
    this.cancellationObserver = cancellationObserver;
  }

  /**
   * Starts a run and returns a single-use mapped event stream. Agent creation and registry
   * ownership are deferred until subscription, so dropping the returned Flux allocates no
   * resources. The service binds Reactor's upstream subscription automatically; WebFlux controllers
   * only return this Flux and never manage a Disposable themselves.
   */
  public Flux<AgentRunEvent> start(RunRequest request) {
    return execute(
        request,
        0L,
        () ->
            factory.create(
                request.context(),
                request.modelAdapter(),
                request.config(),
                request.capabilities(),
                request.history(),
                request.userText(),
                request.attachments()));
  }

  /** Resumes one permission-paused run by delivering persisted decisions as ConfirmResults. */
  public Flux<AgentRunEvent> resume(RunRequest request, ApprovalResumeRequest resume) {
    return execute(
        request,
        resume.checkpointSequence(),
        () ->
            factory.resumeApprovals(
                request.context(),
                request.modelAdapter(),
                request.config(),
                request.capabilities(),
                request.history(),
                resume));
  }

  /** Resumes a server-suspended question and exposes the supplied answer as a tool output. */
  public Flux<AgentRunEvent> resumeQuestion(RunRequest request, QuestionResumeRequest resume) {
    AgentRunEvent.ToolOutputAvailable answer =
        new AgentRunEvent.ToolOutputAvailable(
            request.context().runId(),
            resume.checkpointSequence() + 1,
            java.time.Instant.now(),
            resume.toolCallId(),
            resume.toolName(),
            resume.responseJson(),
            false,
            false);
    return Flux.concat(
        Flux.just(answer),
        execute(
            request,
            resume.checkpointSequence() + 1,
            () ->
                factory.resumeQuestion(
                    request.context(),
                    request.modelAdapter(),
                    request.config(),
                    request.capabilities(),
                    request.history(),
                    resume)));
  }

  private Flux<AgentRunEvent> execute(
      RunRequest request, long initialSequence, Supplier<RunnableAgent> agentSupplier) {
    AtomicBoolean subscribed = new AtomicBoolean();
    return Flux.defer(
        () -> {
          if (!subscribed.compareAndSet(false, true)) {
            return Flux.error(
                new IllegalStateException("An agent run stream can only be subscribed once"));
          }

          RunnableAgent agent;
          try {
            agent = agentSupplier.get();
          } catch (RuntimeException ignored) {
            return Flux.just(internalFailure(request, initialSequence));
          }
          String runId = request.context().runId();
          if (!registry.register(request.context(), agent)) {
            closeAsync(agent);
            return Flux.error(
                new IllegalStateException("An agent run with this id is already active"));
          }

          Flux<AgentRunEvent> events;
          try {
            events = agent.streamEvents();
          } catch (RuntimeException ignored) {
            registry.unregister(runId, agent);
            closeAsync(agent);
            return Flux.just(internalFailure(request, initialSequence));
          }

          return events
              .doFinally(
                  signal -> {
                    if (signal == SignalType.CANCEL) {
                      cancellationObserver.accept(agent.cancelledEvent());
                    }
                    registry.unregister(runId, agent);
                    closeAsync(agent);
                  })
              // Keep this outermost: cancelling the stored subscription must traverse doFinally.
              .doOnSubscribe(subscription -> registry.bindSubscription(runId, agent, subscription));
        });
  }

  private AgentRunEvent.RunFailed internalFailure(RunRequest request, long initialSequence) {
    RunFailureCode code = RunFailureCode.AGENT_INTERNAL;
    return new AgentRunEvent.RunFailed(
        request.context().runId(),
        initialSequence + 1,
        java.time.Instant.now(),
        code.name(),
        code.safeMessage());
  }

  /**
   * Server-initiated cancel. Succeeds only for the run owner; disposes the bound subscription and
   * cooperatively interrupts the agent.
   */
  public boolean cancel(String runId, String tenantId, String userId) {
    return registry.cancel(runId, tenantId, userId);
  }

  public boolean isActive(String runId) {
    return registry.isActive(runId);
  }

  private void closeAsync(RunnableAgent agent) {
    cleanupExecutor.execute(
        () -> {
          try {
            agent.close();
          } catch (Exception ignored) {
            // Cleanup must never mask the terminal signal.
          }
        });
  }
}
