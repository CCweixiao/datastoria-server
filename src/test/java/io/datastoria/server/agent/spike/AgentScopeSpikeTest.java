package io.datastoria.server.agent.spike;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ModelCallEndEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * P4.1 AgentScope Java compatibility spike (ADR-0004). Proves, with a deterministic fake model and
 * no network, that AgentScope {@code v2.0.0} runs under Spring Boot's managed {@code reactor-core
 * 3.7.19} and provides the four capabilities P4 needs: stable streaming
 * (text/reasoning/usage/terminal), a custom model boundary, error propagation, and reactive
 * cancellation that stops the model provider flux on client disconnect.
 *
 * <p>No HTTP, no API key, no real provider. The event sequence and cancel contract pinned here are
 * the foundation for P4.2's internal event model and P4.6's SSE controller.
 */
class AgentScopeSpikeTest {

  private static ReActAgent reactAgentFor(Model model) {
    return ReActAgent.builder()
        .name("spike-assistant")
        .sysPrompt("You are a helpful assistant.")
        .model(model)
        .maxIters(3)
        .build();
  }

  private static Msg userMessage(String text) {
    return Msg.builder().role(MsgRole.USER).textContent(text).build();
  }

  /** Concatenates all {@link TextBlockDeltaEvent} deltas in order. */
  private static String joinText(List<AgentEvent> events) {
    return events.stream()
        .filter(TextBlockDeltaEvent.class::isInstance)
        .map(TextBlockDeltaEvent.class::cast)
        .map(TextBlockDeltaEvent::getDelta)
        .collect(Collectors.joining());
  }

  /** Concatenates all {@link ThinkingBlockDeltaEvent} deltas in order. */
  private static String joinReasoning(List<AgentEvent> events) {
    return events.stream()
        .filter(ThinkingBlockDeltaEvent.class::isInstance)
        .map(ThinkingBlockDeltaEvent.class::cast)
        .map(ThinkingBlockDeltaEvent::getDelta)
        .collect(Collectors.joining());
  }

  /**
   * Pinned event sequence emitted by {@link ReActAgent#streamEvents} for a multi-frame fake-model
   * response. AgentScope correlates sequential same-type frames into a single logical block (Start
   * once, Delta per frame, End once), and carries usage on {@link ModelCallEndEvent}.
   */
  @Test
  void streamEventsProducesExpectedEventSequence() {
    FakeStreamModel model =
        FakeStreamModel.builder()
            .reasoning("Let me think about this. ")
            .reasoning("The answer is a greeting.")
            .text("Hello")
            .text(", world!")
            .finish(7, 9)
            .build();
    ReActAgent agent = reactAgentFor(model);

    List<AgentEvent> events = agent.streamEvents(userMessage("Say hello.")).collectList().block();

    assertThat(events).as("events").isNotNull();
    assertThat(events.stream().map(AgentEvent::getType).toList())
        .containsExactly(
            AgentEventType.AGENT_START,
            AgentEventType.MODEL_CALL_START,
            AgentEventType.THINKING_BLOCK_START,
            AgentEventType.THINKING_BLOCK_DELTA,
            AgentEventType.THINKING_BLOCK_DELTA,
            AgentEventType.THINKING_BLOCK_END,
            AgentEventType.TEXT_BLOCK_START,
            AgentEventType.TEXT_BLOCK_DELTA,
            AgentEventType.TEXT_BLOCK_DELTA,
            AgentEventType.TEXT_BLOCK_END,
            AgentEventType.MODEL_CALL_END,
            AgentEventType.AGENT_RESULT,
            AgentEventType.AGENT_END);

    assertThat(joinText(events)).isEqualTo("Hello, world!");
    assertThat(joinReasoning(events))
        .isEqualTo("Let me think about this. The answer is a greeting.");

    ModelCallEndEvent usageEvent =
        events.stream()
            .filter(ModelCallEndEvent.class::isInstance)
            .map(ModelCallEndEvent.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(usageEvent.getUsage()).as("usage").isNotNull();
    assertThat(usageEvent.getUsage().getInputTokens()).isEqualTo(7);
    assertThat(usageEvent.getUsage().getOutputTokens()).isEqualTo(9);

    assertThat(model.streamInvocations()).as("model.stream invoked exactly once").isEqualTo(1);
  }

  /**
   * De-risks the mandated runtime ({@link HarnessAgent}, docs/design/harness-agent.md): it builds
   * with a temp workspace and every P5+ capability explicitly disabled. Its {@code streamEvents}
   * produces the same terminal events and concatenated text as {@link ReActAgent}, and the fake
   * model verifies that no tool schema reaches the model.
   */
  @Test
  void harnessAgentBuildsAndStreams(@TempDir Path workspace) {
    FakeStreamModel model =
        FakeStreamModel.builder().text("Hello").text(", world!").finish(3, 5).build();

    HarnessAgent agent =
        HarnessAgent.builder()
            .name("spike-harness")
            .sysPrompt("You are a helpful assistant.")
            .model(model)
            .maxIters(3)
            .workspace(workspace)
            .disableCompaction()
            .disableFilesystemTools()
            .disableShellTool()
            .disableMemoryTools()
            .disableMemoryHooks()
            .disableSessionPersistence()
            .disableWorkspaceContext()
            .disableAtPathExpansion()
            .disableSubagents()
            .disableDynamicSubagents()
            .disableDynamicSkills()
            .disableDefaultWorkspaceSkills()
            .disableToolsConfig()
            .build();
    // AgentScope 2.0.0 registers this async helper even when all optional capabilities are off.
    // P4 has no async tools, so remove it explicitly to keep the model boundary tool-free.
    agent.getToolkit().removeTool("wait_async_results");

    try (agent) {
      List<AgentEvent> events = agent.streamEvents(userMessage("hi")).collectList().block();

      assertThat(events).as("events").isNotNull();
      assertThat(events.stream().map(AgentEvent::getType).toList())
          .endsWith(
              AgentEventType.MODEL_CALL_END, AgentEventType.AGENT_RESULT, AgentEventType.AGENT_END);
      assertThat(joinText(events)).isEqualTo("Hello, world!");
      assertThat(model.lastToolCount()).as("P4 minimal Harness exposes no tools").isZero();
    }
  }

  /** A failing model {@link Flux} propagates through {@code streamEvents} as {@code onError}. */
  @Test
  void modelErrorPropagatesAsOnError() {
    Model model =
        new Model() {
          @Override
          public Flux<ChatResponse> stream(
              List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            return Flux.error(new IllegalStateException("provider Boom"));
          }

          @Override
          public String getModelName() {
            return "err-spike";
          }
        };
    ReActAgent agent = reactAgentFor(model);

    StepVerifier.create(agent.streamEvents(userMessage("go")))
        .expectNextCount(2)
        .expectErrorMatches(
            error -> {
              Throwable cause = error;
              while (cause.getCause() != null) {
                cause = cause.getCause();
              }
              return cause instanceof IllegalStateException
                  && cause.getMessage() != null
                  && cause.getMessage().contains("Boom");
            })
        .verify(Duration.ofSeconds(10));
  }

  /**
   * The reliable cancel primitive for DataStoria: disposing the {@code streamEvents} subscription
   * (what a WebFlux client disconnect does) propagates upstream and cancels the model provider
   * flux, so token emission stops. This is the reactive path; {@code Agent.interrupt()} is a
   * cooperative, step-boundary signal that does not abort an in-flight single-step model call (see
   * ADR-0004).
   */
  @Test
  void disposingSubscriptionCancelsModelFlux() throws Exception {
    FakeStreamModel model =
        FakeStreamModel.builder()
            .text("a")
            .text("b")
            .text("c")
            .text("d")
            .finish(1, 4)
            .perFrameDelay(Duration.ofMillis(150))
            .build();
    ReActAgent agent = reactAgentFor(model);

    CountDownLatch firstEvent = new CountDownLatch(1);
    BaseSubscriber<AgentEvent> sub =
        new BaseSubscriber<>() {
          @Override
          protected void hookOnNext(AgentEvent value) {
            firstEvent.countDown();
            cancel();
          }
        };
    agent.streamEvents(userMessage("go")).subscribe(sub);

    assertThat(firstEvent.await(5, TimeUnit.SECONDS)).as("received at least one event").isTrue();
    Thread.sleep(400); // allow cancellation to propagate upstream

    assertThat(model.wasCancelled())
        .as("model provider flux cancelled on subscription dispose")
        .isTrue();
  }
}
