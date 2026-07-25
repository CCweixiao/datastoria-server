package io.datastoria.server.agent.runtime;

import java.util.Optional;

import io.agentscope.core.message.Msg;
import io.agentscope.harness.agent.HarnessAgent;
import io.datastoria.server.agent.domain.AgentRunEvent;

import reactor.core.publisher.Flux;

/**
 * {@link RunnableAgent} backed by a {@link HarnessAgent}. Wires the AgentScope event stream through
 * {@link AgentEventMapper}, explicitly consumes errors into {@link AgentRunEvent.RunFailed}, and
 * invokes {@code interrupt()} on cancellation for cooperative step-boundary cleanup.
 *
 * <p>Cancel contract (ADR-0004 §3.3): disposing the returned {@link Flux} subscription propagates
 * upstream and cancels the provider model flux (stops token emission); {@code doOnCancel}
 * additionally calls {@code agent.interrupt()}. For single-step text runs {@code interrupt()} is
 * effectively a no-op at the step boundary; the reliable stop is the dispose itself.
 */
final class HarnessRunnableAgent implements RunnableAgent {

  private final String runId;
  private final HarnessAgent agent;
  private final Msg userMsg;
  private final AgentEventMapper mapper;

  HarnessRunnableAgent(String runId, HarnessAgent agent, Msg userMsg, AgentEventMapper mapper) {
    this.runId = runId;
    this.agent = agent;
    this.userMsg = userMsg;
    this.mapper = mapper;
  }

  @Override
  public Flux<AgentRunEvent> streamEvents() {
    return agent
        .streamEvents(userMsg)
        .doOnCancel(agent::interrupt)
        .map(mapper::toEvent)
        .filter(Optional::isPresent)
        .map(Optional::get)
        // Explicitly consume stream errors so the subscriber never sees onError; the sanitized
        // RunFailed carries no provider/prompt/credential text.
        .onErrorResume(error -> Flux.just(mapper.failure(error)));
  }

  @Override
  public AgentRunEvent.RunCancelled cancelledEvent() {
    return mapper.cancelled();
  }

  @Override
  public void interrupt() {
    agent.interrupt();
  }

  @Override
  public void close() {
    try {
      agent.close();
    } catch (Exception ignored) {
      // Cleanup must never propagate into the reactive pipeline that triggered it.
    }
  }

  String runId() {
    return runId;
  }
}
