package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
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
import io.github.ccweixiao.datastoria.agent.application.ChatAttachment;
import io.github.ccweixiao.datastoria.agent.application.ChatTurn;
import io.github.ccweixiao.datastoria.agent.testing.FakeModelAdapter;
import io.github.ccweixiao.datastoria.agent.testing.FakeStreamModel;
import io.github.ccweixiao.datastoria.common.agent.AgentRunEvent;
import io.github.ccweixiao.datastoria.common.agent.RunContext;

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
    HarnessAgentFactory factory = TestHarnessAgentFactories.create();

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
  void sendsCurrentAndHistoricalImagesToAgentScopeModel() {
    CapturingMessageModel model = new CapturingMessageModel();

    TestHarnessAgentFactories.create()
        .create(
            ctx("run-images"),
            new FakeModelAdapter(model),
            AgentRuntimeConfig.minimal("sys"),
            AgentRunCapabilities.none(),
            List.of(
                new ChatTurn(
                    "user",
                    "history",
                    List.of(
                        new ChatAttachment(
                            "image/png", "https://example.test/history.png", "history.png")))),
            "",
            List.of(
                new ChatAttachment("image/png", "data:image/png;base64,aGVsbG8=", "current.png")))
        .streamEvents()
        .collectList()
        .block();

    assertThat(
            model.messages.stream()
                .flatMap(message -> message.getContentBlocks(DataBlock.class).stream()))
        .hasSize(2);
  }

  @Test
  void appliesReasoningEffortToAgentScopeGenerateOptions() {
    CapturingMessageModel model = new CapturingMessageModel();

    TestHarnessAgentFactories.create()
        .create(
            ctx("run-reasoning"),
            new FakeModelAdapter(model),
            AgentRuntimeConfig.minimal("sys").withRequestOptions("sys", "high", true),
            "explain")
        .streamEvents()
        .collectList()
        .block();

    assertThat(model.options.getReasoningEffort()).isEqualTo("high");
  }

  @Test
  void eachRunIsIndependentAndScopedByRunId() {
    FakeStreamModel model = FakeStreamModel.builder().text("x").finish(1, 1).build();
    HarnessAgentFactory factory = TestHarnessAgentFactories.create();

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
  void compactsLongHistoryWithoutWorkspaceMemoryFlushOrOffload() {
    CompactionModel model = new CompactionModel();
    List<ChatTurn> history = new java.util.ArrayList<>();
    for (int i = 0; i < 55; i++) {
      history.add(new ChatTurn(i % 2 == 0 ? "user" : "assistant", "history-" + i));
    }

    List<AgentRunEvent> events =
        TestHarnessAgentFactories.create()
            .create(
                ctx("run-compaction"),
                new FakeModelAdapter(model),
                AgentRuntimeConfig.minimal("sys"),
                history,
                "current request")
            .streamEvents()
            .collectList()
            .block();

    assertThat(events).anyMatch(AgentRunEvent.RunCompleted.class::isInstance);
    assertThat(model.calls()).isEqualTo(2);
    assertThat(model.observedCompactedSummary()).isTrue();
  }

  @Test
  void exposesDatabaseSkillsAndServerToolsAtTheModelBoundary() {
    FakeStreamModel model = FakeStreamModel.builder().text("ok").finish(1, 1).build();
    HarnessAgentFactory factory = TestHarnessAgentFactories.create();
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
    HarnessAgentFactory factory = TestHarnessAgentFactories.create();
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
    HarnessAgentFactory factory = TestHarnessAgentFactories.create();
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
        TestHarnessAgentFactories.create()
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
        TestHarnessAgentFactories.create()
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

  @Test
  void approvalResumeUsesConfirmResultAndContinuesSequence() {
    String runId = "run-resume-" + UUID.randomUUID();
    RunContext context = ctx(runId);
    HarnessAgentFactory factory = TestHarnessAgentFactories.create();
    ToolCallingModel model = new ToolCallingModel();
    TestTools tools = new TestTools();
    AgentRunCapabilities capabilities = askCapabilities(tools);

    List<AgentRunEvent> paused =
        factory
            .create(
                context,
                new FakeModelAdapter(model),
                AgentRuntimeConfig.minimal("sys"),
                capabilities,
                List.of(),
                "call the tool")
            .streamEvents()
            .collectList()
            .block();
    AgentRunEvent.ToolApprovalRequired approval =
        (AgentRunEvent.ToolApprovalRequired)
            paused.stream()
                .filter(AgentRunEvent.ToolApprovalRequired.class::isInstance)
                .findFirst()
                .orElseThrow();

    List<AgentRunEvent> resumed =
        factory
            .resumeApprovals(
                context,
                new FakeModelAdapter(model),
                AgentRuntimeConfig.minimal("sys"),
                capabilities,
                new ApprovalResumeRequest(
                    approval.sequence(),
                    approval.replyId(),
                    List.of(
                        new ApprovalResumeRequest.Decision(
                            "permission-call", "server_test_tool", Map.of(), true))))
            .streamEvents()
            .collectList()
            .block();

    assertThat(resumed).isNotNull();
    assertThat(tools.invocations()).isEqualTo(1);
    assertThat(resumed).anyMatch(AgentRunEvent.RunCompleted.class::isInstance);
    assertThat(resumed)
        .allSatisfy(event -> assertThat(event.sequence()).isGreaterThan(approval.sequence()));
    assertThat(resumed).noneMatch(AgentRunEvent.RunStarted.class::isInstance);
  }

  @Test
  void denialResumeLoadsNativeAgentScopeStateAfterProcessRestart() {
    String runId = "run-restart-" + UUID.randomUUID();
    RunContext context = ctx(runId);
    ToolCallingModel model = new ToolCallingModel();
    TestTools tools = new TestTools();
    AgentRunCapabilities capabilities = askCapabilities(tools);
    var nativeStateStore = new io.agentscope.core.state.InMemoryAgentStateStore();
    HarnessAgentFactory beforeRestart = TestHarnessAgentFactories.create(nativeStateStore);
    List<AgentRunEvent> paused =
        beforeRestart
            .create(
                context,
                new FakeModelAdapter(model),
                AgentRuntimeConfig.minimal("sys"),
                capabilities,
                List.of(),
                "call the tool")
            .streamEvents()
            .collectList()
            .block();
    AgentRunEvent.ToolApprovalRequired approval =
        (AgentRunEvent.ToolApprovalRequired)
            paused.stream()
                .filter(AgentRunEvent.ToolApprovalRequired.class::isInstance)
                .findFirst()
                .orElseThrow();

    HarnessAgentFactory afterRestart = TestHarnessAgentFactories.create(nativeStateStore);
    List<AgentRunEvent> resumed =
        afterRestart
            .resumeApprovals(
                context,
                new FakeModelAdapter(model),
                AgentRuntimeConfig.minimal("sys"),
                capabilities,
                new ApprovalResumeRequest(
                    approval.sequence(),
                    approval.replyId(),
                    List.of(
                        new ApprovalResumeRequest.Decision(
                            "permission-call", "server_test_tool", Map.of(), false))))
            .streamEvents()
            .collectList()
            .block();

    assertThat(resumed).isNotNull();
    assertThat(tools.invocations()).isZero();
    assertThat(resumed).anyMatch(AgentRunEvent.RunCompleted.class::isInstance);
    assertThat(model.observedDeniedResult()).isTrue();
  }

  @Test
  void questionSuspendsAndResponseResumesAfterProcessRestart() {
    String runId = "run-question-" + UUID.randomUUID();
    RunContext context = ctx(runId);
    QuestionCallingModel model = new QuestionCallingModel();
    AgentRunCapabilities capabilities =
        new AgentRunCapabilities(List.of(), List.of(new HumanInteractionAgentTools()));
    var nativeStateStore = new io.agentscope.core.state.InMemoryAgentStateStore();
    List<AgentRunEvent> paused =
        TestHarnessAgentFactories.create(nativeStateStore)
            .create(
                context,
                new FakeModelAdapter(model),
                AgentRuntimeConfig.minimal("sys"),
                capabilities,
                List.of(),
                "ask me")
            .streamEvents()
            .collectList()
            .block();
    AgentRunEvent.QuestionRequired question =
        (AgentRunEvent.QuestionRequired)
            paused.stream()
                .filter(AgentRunEvent.QuestionRequired.class::isInstance)
                .findFirst()
                .orElseThrow();

    List<AgentRunEvent> resumed =
        TestHarnessAgentFactories.create(nativeStateStore)
            .resumeQuestion(
                context,
                new FakeModelAdapter(model),
                AgentRuntimeConfig.minimal("sys"),
                capabilities,
                new QuestionResumeRequest(
                    question.sequence(),
                    question.replyId(),
                    question.actionId(),
                    question.toolCallId(),
                    question.toolName(),
                    Map.of("questions", List.of(Map.of("question", "Which cluster?"))),
                    "{\"answer\":\"prod\"}"))
            .streamEvents()
            .collectList()
            .block();

    assertThat(paused).noneMatch(AgentRunEvent.RunCompleted.class::isInstance);
    assertThat(resumed).anyMatch(AgentRunEvent.RunCompleted.class::isInstance);
    assertThat(resumed)
        .allSatisfy(event -> assertThat(event.sequence()).isGreaterThan(question.sequence() + 1));
    assertThat(model.observedAnswer()).isEqualTo("{\"answer\":\"prod\"}");
  }

  @Test
  void questionResumeRejectsMissingNativeAgentScopeState() {
    RunContext context = ctx("run-missing-native-state-" + UUID.randomUUID());
    QuestionCallingModel model = new QuestionCallingModel();
    AgentRunCapabilities capabilities =
        new AgentRunCapabilities(List.of(), List.of(new HumanInteractionAgentTools()));
    HarnessAgentFactory factory = TestHarnessAgentFactories.create();
    QuestionResumeRequest resume =
        new QuestionResumeRequest(
            1L,
            "reply",
            "action",
            "question-call",
            "ask_user_question",
            Map.of(),
            "{\"answer\":\"prod\"}");

    assertThatThrownBy(
            () ->
                factory.resumeQuestion(
                    context,
                    new FakeModelAdapter(model),
                    AgentRuntimeConfig.minimal("sys"),
                    capabilities,
                    resume))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AgentScope state is unavailable");
  }

  private static AgentRunCapabilities askCapabilities(TestTools tools) {
    PermissionContextState permissionContext =
        PermissionContextState.builder()
            .addAskRule(
                "server_test_tool",
                new PermissionRule(
                    "server_test_tool", null, PermissionBehavior.ASK, "datastoria-test-policy"))
            .build();
    return new AgentRunCapabilities(List.of(), List.of(tools), permissionContext);
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

  static final class CapturingMessageModel implements Model {
    private volatile List<Msg> messages = List.of();
    private volatile GenerateOptions options;

    @Override
    public Flux<ChatResponse> stream(
        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
      this.messages = List.copyOf(messages);
      this.options = options;
      return Flux.just(
          ChatResponse.builder().content(List.of(TextBlock.builder().text("ok").build())).build(),
          ChatResponse.builder()
              .content(List.of())
              .usage(ChatUsage.builder().inputTokens(1).outputTokens(1).time(0.0).build())
              .finishReason("stop")
              .metadata(Map.of())
              .build());
    }

    @Override
    public String getModelName() {
      return "capture";
    }

    @Override
    public int getContextWindowSize() {
      return 128_000;
    }
  }

  static final class ToolCallingModel implements Model {
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean observedDeniedResult;

    @Override
    public Flux<ChatResponse> stream(
        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
      if (calls.incrementAndGet() > 1) {
        observedDeniedResult =
            messages.stream()
                .flatMap(message -> message.getContent().stream())
                .filter(io.agentscope.core.message.ToolResultBlock.class::isInstance)
                .map(io.agentscope.core.message.ToolResultBlock.class::cast)
                .anyMatch(
                    result ->
                        result.getState() == io.agentscope.core.message.ToolResultState.DENIED);
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

    boolean observedDeniedResult() {
      return observedDeniedResult;
    }
  }

  static final class QuestionCallingModel implements Model {
    private final AtomicInteger calls = new AtomicInteger();
    private volatile String observedAnswer;

    @Override
    public Flux<ChatResponse> stream(
        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
      if (calls.incrementAndGet() > 1) {
        observedAnswer =
            messages.stream()
                .flatMap(message -> message.getContent().stream())
                .filter(ToolResultBlock.class::isInstance)
                .map(ToolResultBlock.class::cast)
                .flatMap(result -> result.getOutput().stream())
                .filter(TextBlock.class::isInstance)
                .map(TextBlock.class::cast)
                .map(TextBlock::getText)
                .findFirst()
                .orElse(null);
        return Flux.just(
            ChatResponse.builder()
                .content(List.of(TextBlock.builder().text("thanks").build()))
                .build(),
            ChatResponse.builder()
                .content(List.of())
                .usage(ChatUsage.builder().inputTokens(1).outputTokens(1).time(0.0).build())
                .finishReason("stop")
                .metadata(Map.of())
                .build());
      }
      ToolUseBlock call =
          ToolUseBlock.builder()
              .id("question-call")
              .name("ask_user_question")
              .input(
                  Map.of(
                      "questions",
                      List.of(
                          Map.of(
                              "header",
                              "Which cluster?",
                              "options",
                              List.of(
                                  Map.of("id", "o1", "label", "Prod", "input", "none"),
                                  Map.of("id", "o2", "label", "Staging", "input", "none"))))))
              .content(
                  "{\"questions\":[{\"header\":\"Which cluster?\",\"options\":[{\"id\":\"o1\","
                      + " \"label\":\"Prod\",\"input\":\"none\"},{\"id\":\"o2\",\"label\":"
                      + " \"Staging\",\"input\":\"none\"}]}]}")
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
      return "question-test-model";
    }

    String observedAnswer() {
      return observedAnswer;
    }
  }

  static final class CompactionModel implements Model {
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean observedCompactedSummary;

    @Override
    public Flux<ChatResponse> stream(
        List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
      int call = calls.incrementAndGet();
      if (call == 1) {
        return response("summary-preserves-intent");
      }
      observedCompactedSummary =
          messages.stream()
              .flatMap(message -> message.getContent().stream())
              .filter(TextBlock.class::isInstance)
              .map(TextBlock.class::cast)
              .map(TextBlock::getText)
              .anyMatch(text -> text.contains("summary-preserves-intent"));
      return response("done");
    }

    private Flux<ChatResponse> response(String text) {
      return Flux.just(
          ChatResponse.builder().content(List.of(TextBlock.builder().text(text).build())).build(),
          ChatResponse.builder()
              .content(List.of())
              .usage(ChatUsage.builder().inputTokens(1).outputTokens(1).time(0.0).build())
              .finishReason("stop")
              .metadata(Map.of())
              .build());
    }

    @Override
    public String getModelName() {
      return "compaction-test-model";
    }

    int calls() {
      return calls.get();
    }

    boolean observedCompactedSummary() {
      return observedCompactedSummary;
    }
  }
}
