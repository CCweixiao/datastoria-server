package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.tool.Tool;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.testing.FakeModelAdapter;
import io.datastoria.server.agent.testing.FakeStreamModel;

/**
 * Verifies the {@link HarnessAgentFactory} minimal-permission contract (ADR-0004 §3.5): the
 * produced {@link RunnableAgent} streams mapped events, exposes zero tools at the model boundary,
 * stamps each run's id on its events, and supports interrupt/close without throwing.
 */
class HarnessAgentFactoryTest {

  private static RunContext ctx(String runId) {
    return new RunContext(
        runId, "tenant-a", "user-1", "sess", "msg", "cr", "arev", "mcfg", Instant.EPOCH);
  }

  @Test
  void producedRunnableStreamsEventsAndExposesNoTools() {
    FakeStreamModel model = FakeStreamModel.builder().text("ok").finish(1, 1).build();
    HarnessAgentFactory factory = new HarnessAgentFactory();

    RunnableAgent agent =
        factory.create(
            ctx("run-1"), new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi");

    List<AgentRunEvent> events = agent.streamEvents().collectList().block();

    assertThat(events).isNotNull();
    assertThat(events).isNotEmpty();
    assertThat(events.get(0)).isInstanceOf(AgentRunEvent.RunStarted.class);
    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentRunEvent.RunCompleted.class);
    assertThat(events).allSatisfy(e -> assertThat(e.runId()).isEqualTo("run-1"));
    assertThat(model.lastToolCount())
        .as("P4 minimal Harness exposes no tools at the model boundary")
        .isZero();
  }

  @Test
  void eachRunIsIndependentAndScopedByRunId() {
    FakeStreamModel model = FakeStreamModel.builder().text("x").finish(1, 1).build();
    HarnessAgentFactory factory = new HarnessAgentFactory();

    RunnableAgent a =
        factory.create(
            ctx("run-a"), new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi");
    RunnableAgent b =
        factory.create(
            ctx("run-b"), new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi");

    List<AgentRunEvent> eventsA = a.streamEvents().collectList().block();
    List<AgentRunEvent> eventsB = b.streamEvents().collectList().block();

    assertThat(eventsA).isNotNull();
    assertThat(eventsB).isNotNull();
    assertThat(eventsA).allSatisfy(e -> assertThat(e.runId()).isEqualTo("run-a"));
    assertThat(eventsB).allSatisfy(e -> assertThat(e.runId()).isEqualTo("run-b"));
    // Two stream calls across two independent runnables.
    assertThat(model.streamInvocations()).isEqualTo(2);
  }

  @Test
  void exposesDatabaseSkillsAndServerToolsAtTheModelBoundary() {
    FakeStreamModel model = FakeStreamModel.builder().text("ok").finish(1, 1).build();
    HarnessAgentFactory factory = new HarnessAgentFactory();
    AgentSkill skill =
        AgentSkill.builder()
            .name("diagnose")
            .description("Diagnose ClickHouse")
            .skillContent("Use evidence.")
            .source("datastoria-database")
            .build();

    RunnableAgent agent =
        factory.create(
            ctx("run-tools"),
            new FakeModelAdapter(model),
            AgentRuntimeConfig.minimal("sys"),
            new AgentRunCapabilities(List.of(skill), new TestTools()),
            List.of(),
            "hi");

    agent.streamEvents().collectList().block();

    assertThat(model.lastToolCount())
        .as("AgentScope receives the skill loader and server tool schemas")
        .isGreaterThanOrEqualTo(2);
  }

  @Test
  void interruptAndCloseDoNotThrow() {
    FakeStreamModel model = FakeStreamModel.builder().text("x").finish(1, 1).build();
    HarnessAgentFactory factory = new HarnessAgentFactory();
    RunnableAgent agent =
        factory.create(
            ctx("run-c"), new FakeModelAdapter(model), AgentRuntimeConfig.minimal("sys"), "hi");

    agent.streamEvents().collectList().block();
    agent.interrupt();
    agent.close();
    // No exception thrown == pass.
  }

  static final class TestTools {
    @Tool(name = "server_test_tool", description = "Server-side test tool", readOnly = true)
    public String run() {
      return "ok";
    }
  }
}
