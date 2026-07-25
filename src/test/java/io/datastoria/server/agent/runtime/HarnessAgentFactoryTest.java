package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.tool.Tool;
import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.testing.FakeModelAdapter;
import io.datastoria.server.agent.testing.FakeStreamModel;

import reactor.core.publisher.Flux;

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

  @Test
  void agentScopeAskPolicyPausesInsteadOfExecutingOrCompleting() {
    PermissionContextState permissionContext =
        PermissionContextState.builder()
            .addAskRule(
                "server_test_tool",
                new PermissionRule(
                    "server_test_tool", null, PermissionBehavior.ASK, "datastoria-test-policy"))
            .build();
    HarnessAgentFactory factory = new HarnessAgentFactory();
    RunnableAgent agent =
        factory.create(
            ctx("run-ask-" + UUID.randomUUID()),
            new FakeModelAdapter(new ToolCallingModel()),
            AgentRuntimeConfig.minimal("sys"),
            new AgentRunCapabilities(List.of(), List.of(new TestTools()), permissionContext),
            List.of(),
            "call the tool");

    List<AgentRunEvent> events = agent.streamEvents().collectList().block();

    assertThat(events).isNotNull();
    assertThat(events).anyMatch(AgentRunEvent.ToolApprovalRequired.class::isInstance);
    assertThat(events).noneMatch(AgentRunEvent.RunCompleted.class::isInstance);
    assertThat(
            ((AgentRunEvent.ToolApprovalRequired)
                    events.stream()
                        .filter(AgentRunEvent.ToolApprovalRequired.class::isInstance)
                        .findFirst()
                        .orElseThrow())
                .approvals())
        .singleElement()
        .extracting(AgentRunEvent.ToolApproval::toolName)
        .isEqualTo("server_test_tool");
  }

  @Test
  void agentScopeAllowAndDenyPoliciesControlRealToolExecution() {
    TestTools allowedTools = new TestTools();
    List<AgentRunEvent> allowed =
        new HarnessAgentFactory()
            .create(
                ctx("run-allow-" + UUID.randomUUID()),
                new FakeModelAdapter(new ToolCallingModel()),
                AgentRuntimeConfig.minimal("sys"),
                new AgentRunCapabilities(List.of(), allowedTools),
                List.of(),
                "call the tool")
            .streamEvents()
            .collectList()
            .block();

    assertThat(allowed).isNotNull();
    assertThat(allowedTools.invocations()).isEqualTo(1);
    assertThat(allowed).anyMatch(AgentRunEvent.RunCompleted.class::isInstance);

    TestTools deniedTools = new TestTools();
    PermissionContextState denied =
        PermissionContextState.builder()
            .addDenyRule(
                "server_test_tool",
                new PermissionRule(
                    "server_test_tool", null, PermissionBehavior.DENY, "datastoria-test-policy"))
            .build();
    List<AgentRunEvent> deniedEvents =
        new HarnessAgentFactory()
            .create(
                ctx("run-deny-" + UUID.randomUUID()),
                new FakeModelAdapter(new ToolCallingModel()),
                AgentRuntimeConfig.minimal("sys"),
                new AgentRunCapabilities(List.of(), List.of(deniedTools), denied),
                List.of(),
                "call the tool")
            .streamEvents()
            .collectList()
            .block();

    assertThat(deniedEvents).isNotNull();
    assertThat(deniedTools.invocations()).isZero();
    assertThat(deniedEvents)
        .filteredOn(AgentRunEvent.ToolOutputAvailable.class::isInstance)
        .map(AgentRunEvent.ToolOutputAvailable.class::cast)
        .anyMatch(AgentRunEvent.ToolOutputAvailable::denied);
  }

  static final class TestTools {
    private final AtomicInteger invocations = new AtomicInteger();

    @Tool(name = "server_test_tool", description = "Server-side test tool", readOnly = true)
    public String run() {
      invocations.incrementAndGet();
      return "ok";
    }

    int invocations() {
      return invocations.get();
    }
  }

  static final class ToolCallingModel implements Model {
    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public Flux<ChatResponse> stream(
        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
      if (calls.incrementAndGet() > 1) {
        ChatUsage usage = ChatUsage.builder().inputTokens(1).outputTokens(1).time(0.0).build();
        return Flux.just(
            ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("done").build()))
                .build(),
            ChatResponse.builder()
                .content(List.of())
                .usage(usage)
                .finishReason("stop")
                .metadata(Map.of())
                .build());
      }
      ToolUseBlock call =
          ToolUseBlock.builder()
              .id("permission-call")
              .name("server_test_tool")
              .input(Map.of())
              .content("{}")
              .state(ToolCallState.FINISHED)
              .build();
      return Flux.just(
          ChatResponse.builder()
              .content(List.of(call))
              .finishReason("tool_calls")
              .metadata(Map.of())
              .build());
    }

    @Override
    public String getModelName() {
      return "permission-test-model";
    }
  }
}
