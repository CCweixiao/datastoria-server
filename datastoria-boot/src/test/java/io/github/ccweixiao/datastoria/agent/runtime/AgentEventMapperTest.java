package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.ToolCallDeltaEvent;
import io.agentscope.core.event.ToolCallEndEvent;
import io.agentscope.core.event.ToolCallStartEvent;
import io.agentscope.core.event.ToolResultEndEvent;
import io.agentscope.core.event.ToolResultStartEvent;
import io.agentscope.core.event.ToolResultTextDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.github.ccweixiao.datastoria.agent.testing.FakeModelAdapter;
import io.github.ccweixiao.datastoria.agent.testing.FakeStreamModel;
import io.github.ccweixiao.datastoria.common.agent.AgentRunEvent;
import io.github.ccweixiao.datastoria.common.agent.RunContext;

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
    HarnessAgentFactory factory = TestHarnessAgentFactories.create(FIXED_CLOCK);
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

  @Test
  void mapsToolLifecycleAndPermissionAskWithoutAgentScopeLeakage() {
    AgentEventMapper mapper = new AgentEventMapper(ctx("run-tool"), FIXED_CLOCK);
    List<AgentRunEvent> events =
        List.of(
                new ToolCallStartEvent("reply", "call-1", "execute_sql"),
                new ToolCallDeltaEvent("reply", "call-1", "execute_sql", "{\"sql\":\"SELECT 1\"}"),
                new ToolCallEndEvent("reply", "call-1", "execute_sql"),
                new ToolResultStartEvent("reply", "call-1", "execute_sql"),
                new ToolResultTextDeltaEvent(
                    "reply", "call-1", "execute_sql", "{\"rows\":[{\"value\":1}]}"),
                new ToolResultEndEvent("reply", "call-1", "execute_sql", ToolResultState.SUCCESS),
                new RequireUserConfirmEvent(
                    "reply",
                    List.of(
                        new ToolUseBlock(
                            "call-2", "execute_sql", java.util.Map.of("sql", "SELECT 2")))))
            .stream()
            .map(mapper::toEvent)
            .flatMap(Optional::stream)
            .toList();

    assertThat(events)
        .extracting(event -> event.getClass().getSimpleName())
        .containsExactly(
            "ToolInputStarted",
            "ToolInputDelta",
            "ToolInputAvailable",
            "ToolOutputStarted",
            "ToolOutputDelta",
            "ToolOutputAvailable",
            "ToolApprovalRequired");
    AgentRunEvent.ToolInputAvailable input = (AgentRunEvent.ToolInputAvailable) events.get(2);
    assertThat(input.inputJson()).isEqualTo("{\"sql\":\"SELECT 1\"}");
    AgentRunEvent.ToolOutputAvailable output = (AgentRunEvent.ToolOutputAvailable) events.get(5);
    assertThat(output.outputJson()).isEqualTo("{\"rows\":[{\"value\":1}]}");
    AgentRunEvent.ToolApproval approval =
        ((AgentRunEvent.ToolApprovalRequired) events.get(6)).approvals().get(0);
    assertThat(approval.actionId()).startsWith("act_").hasSize(28);
    assertThat(approval.inputJson()).isEqualTo("{\"sql\":\"SELECT 2\"}");
    assertThat(events)
        .extracting(AgentRunEvent::sequence)
        .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
  }

  @Test
  void pausedAgentResultDoesNotCompleteRun() {
    for (GenerateReason reason :
        List.of(
            GenerateReason.PERMISSION_ASKING,
            GenerateReason.TOOL_SUSPENDED,
            GenerateReason.MIDDLEWARE_STOP_REQUESTED)) {
      AgentEventMapper mapper = new AgentEventMapper(ctx("run-paused"), FIXED_CLOCK);
      Msg result = Msg.builder().textContent("paused").generateReason(reason).build();

      assertThat(mapper.toEvent(new AgentResultEvent(result))).as(reason.name()).isEmpty();
    }
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
