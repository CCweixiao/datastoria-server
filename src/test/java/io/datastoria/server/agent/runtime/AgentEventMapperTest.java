package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.testing.FakeModelAdapter;
import io.datastoria.server.agent.testing.FakeStreamModel;

/**
 * Pins the ADR-0004 §3.2 AgentScope → DataStoria event mapping by driving a deterministic fake
 * model through a real {@link HarnessAgentFactory}-built agent. No network, no API key. Asserts the
 * exact emitted event sequence, delta concatenation, usage values, runId stamping, monotonic
 * sequence, and that the P4 minimal HarnessAgent exposes zero tools at the model boundary.
 */
class AgentEventMapperTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-25T00:00:00Z"), ZoneOffset.UTC);

  private static RunContext ctx(String runId) {
    return new RunContext(
        runId, "tenant-a", "user-1", "sess-1", "msg-1", "cr-1", "arev-1", "mcfg-1", Instant.EPOCH);
  }

  private static List<AgentRunEvent> stream(FakeStreamModel model, String runId) {
    HarnessAgentFactory factory = new HarnessAgentFactory(FIXED_CLOCK);
    RunnableAgent agent =
        factory.create(
            ctx(runId), new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi");
    return agent.streamEvents().collectList().block();
  }

  @Test
  void reasoningAndTextRunMapsToExpectedEventSequence() {
    FakeStreamModel model =
        FakeStreamModel.builder()
            .reasoning("thinking A ")
            .reasoning("thinking B")
            .text("Hello")
            .text(", world!")
            .finish(7, 9)
            .build();

    List<AgentRunEvent> events = stream(model, "run-1");

    assertThat(events).isNotNull();
    assertThat(events.stream().map(e -> e.getClass().getSimpleName()).toList())
        .containsExactly(
            "RunStarted",
            "ReasoningBlockStarted",
            "ReasoningDelta",
            "ReasoningDelta",
            "ReasoningBlockEnded",
            "TextBlockStarted",
            "TextDelta",
            "TextDelta",
            "TextBlockEnded",
            "UsageReported",
            "RunCompleted");

    assertThat(concatReasoning(events)).isEqualTo("thinking A thinking B");
    assertThat(concatText(events)).isEqualTo("Hello, world!");

    AgentRunEvent.UsageReported usage = onlyUsage(events);
    assertThat(usage.usage().inputTokens()).isEqualTo(7);
    assertThat(usage.usage().outputTokens()).isEqualTo(9);
    assertThat(usage.usage().totalTimeSeconds()).isEqualTo(0.0d);

    assertThat(events).allSatisfy(e -> assertThat(e.runId()).isEqualTo("run-1"));
    assertThat(events).allSatisfy(e -> assertThat(e.occurredAt()).isEqualTo(FIXED_CLOCK.instant()));
    List<Long> seqs = events.stream().map(AgentRunEvent::sequence).toList();
    for (int i = 0; i < seqs.size(); i++) {
      assertThat(seqs.get(i)).as("sequence[%d]", i).isEqualTo(i + 1L);
    }

    AgentRunEvent.RunStarted started = (AgentRunEvent.RunStarted) events.get(0);
    assertThat(started.sessionId()).isEqualTo("sess-1");
    assertThat(started.messageId()).isEqualTo("msg-1");

    assertThat(model.streamInvocations()).as("model.stream invoked exactly once").isEqualTo(1);
    assertThat(model.lastToolCount()).as("P4 minimal Harness exposes no tools").isZero();
  }

  @Test
  void textOnlyRunOmitsReasoningEvents() {
    FakeStreamModel model = FakeStreamModel.builder().text("hi").finish(1, 1).build();

    List<AgentRunEvent> events = stream(model, "run-2");

    assertThat(events).isNotNull();
    assertThat(events.stream().map(e -> e.getClass().getSimpleName()).toList())
        .containsExactly(
            "RunStarted",
            "TextBlockStarted",
            "TextDelta",
            "TextBlockEnded",
            "UsageReported",
            "RunCompleted");
  }

  private static String concatReasoning(List<AgentRunEvent> events) {
    return events.stream()
        .filter(AgentRunEvent.ReasoningDelta.class::isInstance)
        .map(AgentRunEvent.ReasoningDelta.class::cast)
        .map(AgentRunEvent.ReasoningDelta::delta)
        .reduce("", String::concat);
  }

  private static String concatText(List<AgentRunEvent> events) {
    return events.stream()
        .filter(AgentRunEvent.TextDelta.class::isInstance)
        .map(AgentRunEvent.TextDelta.class::cast)
        .map(AgentRunEvent.TextDelta::delta)
        .reduce("", String::concat);
  }

  private static AgentRunEvent.UsageReported onlyUsage(List<AgentRunEvent> events) {
    return events.stream()
        .filter(AgentRunEvent.UsageReported.class::isInstance)
        .map(AgentRunEvent.UsageReported.class::cast)
        .findFirst()
        .orElseThrow();
  }
}
