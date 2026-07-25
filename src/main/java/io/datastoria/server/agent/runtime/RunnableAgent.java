package io.datastoria.server.agent.runtime;

import io.datastoria.server.agent.domain.AgentRunEvent;

import reactor.core.publisher.Flux;

/**
 * Run-scoped, AgentScope-backed agent exposed to the application layer as an AgentScope-free
 * handle. {@link #streamEvents()} emits already-mapped {@link AgentRunEvent}s with errors
 * explicitly consumed (mapped to {@code RunFailed}); {@link #interrupt()} is the cooperative cancel
 * signal; and {@link #close()} releases AgentScope resources.
 *
 * <p>Implementations must not be reused across runs (per docs/design/harness-agent.md §4: stateful
 * instances are run-scoped, never shared across users).
 */
public interface RunnableAgent extends AutoCloseable {

  /** The mapped event stream for this run. Errors are consumed into {@code RunFailed}. */
  Flux<AgentRunEvent> streamEvents();

  /**
   * Creates the cancellation terminal event for lifecycle persistence. It is delivered out of band
   * because a cancelled reactive subscriber cannot receive another onNext signal.
   */
  AgentRunEvent.RunCancelled cancelledEvent();

  /** Cooperative, step-boundary cancel signal (does not abort an in-flight single-step call). */
  void interrupt();

  /** Releases AgentScope resources. Must not throw. */
  @Override
  void close();
}
