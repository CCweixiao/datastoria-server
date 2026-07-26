package io.datastoria.server.agent.runtime;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.datastoria.server.agent.application.ChatAttachment;
import io.datastoria.server.agent.application.ChatToolExchange;
import io.datastoria.server.agent.application.ChatTurn;
import io.datastoria.server.agent.domain.RunContext;

import reactor.core.publisher.Flux;

/**
 * Builds a run-scoped {@link RunnableAgent} backed by a minimal-permission {@link HarnessAgent}.
 *
 * <p>Only DataStoria-authorized run-scoped tools and immutable Skill revisions are exposed.
 * AgentScope filesystem, shell, memory, subagent, workspace-context, default-workspace-Skill and
 * tools-config capabilities remain disabled. The residual {@code wait_async_results} helper is
 * removed; dynamic Skill loading is enabled only when the server supplied a pinned Skill repository
 * for the run.
 *
 * <p>AgentScope's {@code AgentTraceMiddleware} is also disabled ({@code
 * enableAgentTracingLog(false)}): it logs model output and raw exception {@code toString()} at
 * INFO, which could echo prompt/context fragments or credential-bearing provider errors. Disabling
 * it does not affect error handling — stream errors are still explicitly consumed into {@code
 * RunFailed} downstream; DataStoria's own redacted observability (docs/design/harness-agent.md §12)
 * replaces this verbose trace.
 *
 * <p>User and historical messages are normalized to AgentScope {@code Msg}s here, including
 * validated image data/URL attachments. Provider reasoning options remain server-controlled.
 */
public final class HarnessAgentFactory {

  private final Clock clock;
  private final AgentToolRegistry toolRegistry;
  private final AgentStateStore stateStore;

  public HarnessAgentFactory() {
    this(Clock.systemUTC(), new AgentToolRegistry());
  }

  public HarnessAgentFactory(Clock clock) {
    this(clock, new AgentToolRegistry());
  }

  public HarnessAgentFactory(AgentToolRegistry toolRegistry) {
    this(Clock.systemUTC(), toolRegistry);
  }

  public HarnessAgentFactory(Clock clock, AgentToolRegistry toolRegistry) {
    this(clock, toolRegistry, new InMemoryAgentStateStore());
  }

  public HarnessAgentFactory(
      Clock clock, AgentToolRegistry toolRegistry, AgentStateStore stateStore) {
    this.clock = clock;
    this.toolRegistry = toolRegistry;
    this.stateStore = stateStore;
  }

  public RunnableAgent create(
      RunContext context, ModelAdapter modelAdapter, AgentRuntimeConfig config, String userText) {
    return create(context, modelAdapter, config, AgentRunCapabilities.none(), List.of(), userText);
  }

  public RunnableAgent create(
      RunContext context,
      ModelAdapter modelAdapter,
      AgentRuntimeConfig config,
      List<ChatTurn> history,
      String userText) {
    return create(context, modelAdapter, config, AgentRunCapabilities.none(), history, userText);
  }

  public RunnableAgent create(
      RunContext context,
      ModelAdapter modelAdapter,
      AgentRuntimeConfig config,
      AgentRunCapabilities capabilities,
      List<ChatTurn> history,
      String userText) {
    return create(context, modelAdapter, config, capabilities, history, userText, List.of());
  }

  public RunnableAgent create(
      RunContext context,
      ModelAdapter modelAdapter,
      AgentRuntimeConfig config,
      AgentRunCapabilities capabilities,
      List<ChatTurn> history,
      String userText,
      List<ChatAttachment> attachments) {
    Toolkit toolkit = toolRegistry.createToolkit(capabilities.tools());
    PermissionContextState permissionContext =
        capabilities.permissionContext() == null
            ? allowRegisteredServerTools(toolkit)
            : capabilities.permissionContext();
    HarnessAgent agent =
        buildAgent(context, modelAdapter, config, capabilities, toolkit, permissionContext);

    List<Msg> messages = historyMessages(history);
    messages.add(userMessage(userText, attachments));
    return runnable(context, agent, messages, 0L, config.outputReasoning());
  }

  /**
   * Recreates a paused AgentScope call from the shared state store, or primes the minimal safe
   * state from persisted chat + pending tool metadata after a JVM restart.
   */
  public RunnableAgent resumeApprovals(
      RunContext context,
      ModelAdapter modelAdapter,
      AgentRuntimeConfig config,
      AgentRunCapabilities capabilities,
      List<ChatTurn> history,
      ApprovalResumeRequest resume) {
    Toolkit toolkit = toolRegistry.createToolkit(capabilities.tools());
    PermissionContextState permissionContext =
        capabilities.permissionContext() == null
            ? allowRegisteredServerTools(toolkit)
            : capabilities.permissionContext();
    List<ToolUseBlock> pending =
        resume.decisions().stream()
            .map(
                decision ->
                    ToolUseBlock.builder()
                        .id(decision.toolCallId())
                        .name(decision.toolName())
                        .input(decision.input())
                        .content(writeToolInput(decision.input()))
                        .state(ToolCallState.ASKING)
                        .build())
            .toList();
    primeRestartState(context, history, pending, permissionContext, resume.replyId());

    List<ConfirmResult> confirmations = new ArrayList<>();
    for (int i = 0; i < pending.size(); i++) {
      confirmations.add(new ConfirmResult(resume.decisions().get(i).confirmed(), pending.get(i)));
    }
    Msg confirmation =
        Msg.builder()
            .role(MsgRole.USER)
            .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, confirmations))
            .build();
    HarnessAgent agent =
        buildAgent(context, modelAdapter, config, capabilities, toolkit, permissionContext);
    return runnable(
        context,
        agent,
        List.of(confirmation),
        resume.checkpointSequence(),
        config.outputReasoning());
  }

  /** Restores a server-suspended question and supplies its durable response as a tool result. */
  public RunnableAgent resumeQuestion(
      RunContext context,
      ModelAdapter modelAdapter,
      AgentRuntimeConfig config,
      AgentRunCapabilities capabilities,
      List<ChatTurn> history,
      QuestionResumeRequest resume) {
    Toolkit toolkit = toolRegistry.createToolkit(capabilities.tools());
    PermissionContextState permissionContext =
        capabilities.permissionContext() == null
            ? allowRegisteredServerTools(toolkit)
            : capabilities.permissionContext();
    ToolUseBlock pending =
        ToolUseBlock.builder()
            .id(resume.toolCallId())
            .name(resume.toolName())
            .input(resume.input())
            .content(writeToolInput(resume.input()))
            .state(ToolCallState.ALLOWED)
            .build();
    primeRestartState(context, history, List.of(pending), permissionContext, resume.replyId());

    ToolResultBlock result =
        new ToolResultBlock(
            resume.toolCallId(),
            resume.toolName(),
            List.of(TextBlock.builder().text(resume.responseJson()).build()),
            Map.of(),
            ToolResultState.SUCCESS);
    HarnessAgent agent =
        buildAgent(context, modelAdapter, config, capabilities, toolkit, permissionContext);
    // checkpoint+1 is reserved for the synthetic ToolOutputAvailable emitted by AgentRunService.
    return runnable(
        context,
        agent,
        List.of(new ToolResultMessage(result)),
        resume.checkpointSequence() + 1,
        config.outputReasoning());
  }

  private HarnessAgent buildAgent(
      RunContext context,
      ModelAdapter modelAdapter,
      AgentRuntimeConfig config,
      AgentRunCapabilities capabilities,
      Toolkit toolkit,
      PermissionContextState permissionContext) {
    Model model = configuredModel(modelAdapter.modelFor(context), config);
    HarnessAgent.Builder builder =
        HarnessAgent.builder()
            .name("run-" + context.runId())
            .sysPrompt(config.systemPrompt())
            .model(model)
            .toolkit(toolkit)
            .permissionContext(permissionContext)
            .stateStore(stateStore)
            .maxIters(config.maxIters());
    if (!capabilities.skills().isEmpty()) {
      builder.skillRepository(new InMemoryAgentSkillRepository(capabilities.skills()));
    } else {
      builder.disableDynamicSkills();
    }
    HarnessAgent agent =
        builder
            .compaction(
                CompactionConfig.builder()
                    .flushBeforeCompact(false)
                    .offloadBeforeCompact(false)
                    .model(safeCompactionModel(model))
                    .build())
            .disableFilesystemTools()
            .disableShellTool()
            .disableMemoryTools()
            .disableMemoryHooks()
            .disableWorkspaceContext()
            .disableAtPathExpansion()
            .disableSubagents()
            .disableDynamicSubagents()
            .disableDefaultWorkspaceSkills()
            .disableToolsConfig()
            .enableAgentTracingLog(false)
            .build();
    agent.getToolkit().removeTool("wait_async_results");
    return agent;
  }

  private Model safeCompactionModel(Model delegate) {
    return new Model() {
      @Override
      public Flux<ChatResponse> stream(
          List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return delegate.stream(messages, tools, options)
            .onErrorMap(
                ignored ->
                    new IllegalStateException("Model unavailable during context compaction"));
      }

      @Override
      public String getModelName() {
        return delegate.getModelName();
      }

      @Override
      public int getContextWindowSize() {
        return delegate.getContextWindowSize();
      }
    };
  }

  private Model configuredModel(Model delegate, AgentRuntimeConfig config) {
    if (config.reasoningEffort() == null) {
      return delegate;
    }
    return new Model() {
      @Override
      public Flux<ChatResponse> stream(
          List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        GenerateOptions requestOptions =
            GenerateOptions.builder().reasoningEffort(config.reasoningEffort()).build();
        return delegate.stream(
            messages, tools, GenerateOptions.mergeOptions(options, requestOptions));
      }

      @Override
      public String getModelName() {
        return delegate.getModelName();
      }

      @Override
      public int getContextWindowSize() {
        return delegate.getContextWindowSize();
      }
    };
  }

  private RunnableAgent runnable(
      RunContext context,
      HarnessAgent agent,
      List<Msg> messages,
      long initialSequence,
      boolean outputReasoning) {
    return new HarnessRunnableAgent(
        context.runId(),
        agent,
        messages,
        new AgentEventMapper(context, clock, initialSequence, outputReasoning),
        RuntimeContext.builder()
            .userId(context.userId())
            // AgentScope state is run-scoped. Chat history is reconstructed by DataStoria.
            .sessionId(context.runId())
            .build());
  }

  private void primeRestartState(
      RunContext context,
      List<ChatTurn> history,
      List<ToolUseBlock> pending,
      PermissionContextState permissionContext,
      String replyId) {
    if (stateStore.exists(context.userId(), context.runId())) {
      return;
    }
    List<Msg> restored = historyMessages(history);
    restored.add(
        Msg.builder()
            .role(MsgRole.ASSISTANT)
            .content(pending.stream().map(ContentBlock.class::cast).toList())
            .build());
    AgentState state =
        AgentState.builder()
            .userId(context.userId())
            .sessionId(context.runId())
            .replyId(replyId)
            .context(restored)
            .permissionContext(permissionContext)
            .build();
    stateStore.save(context.userId(), context.runId(), "agent_state", state);
  }

  private List<Msg> historyMessages(List<ChatTurn> history) {
    List<Msg> messages = new ArrayList<>();
    for (ChatTurn turn : history) {
      MsgRole role = "assistant".equals(turn.role()) ? MsgRole.ASSISTANT : MsgRole.USER;
      if (role == MsgRole.USER) {
        messages.add(userMessage(turn.text(), turn.attachments()));
        continue;
      }
      List<ContentBlock> assistantContent = new ArrayList<>();
      if (turn.text() != null && !turn.text().isBlank()) {
        assistantContent.add(TextBlock.builder().text(turn.text()).build());
      }
      for (ChatToolExchange exchange : turn.toolExchanges()) {
        assistantContent.add(
            ToolUseBlock.builder()
                .id(exchange.toolCallId())
                .name(exchange.toolName())
                .input(readToolInput(exchange.inputJson()))
                .content(exchange.inputJson())
                .state(ToolCallState.FINISHED)
                .build());
      }
      if (!assistantContent.isEmpty()) {
        messages.add(Msg.builder().role(role).content(assistantContent).build());
      }
      for (ChatToolExchange exchange : turn.toolExchanges()) {
        messages.add(
            new ToolResultMessage(
                new ToolResultBlock(
                    exchange.toolCallId(),
                    exchange.toolName(),
                    List.of(TextBlock.builder().text(exchange.outputJson()).build()),
                    Map.of(),
                    exchange.error() ? ToolResultState.ERROR : ToolResultState.SUCCESS)));
      }
    }
    return messages;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readToolInput(String inputJson) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().readValue(inputJson, Map.class);
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  private Msg userMessage(String text, List<ChatAttachment> attachments) {
    List<ContentBlock> content = new ArrayList<>();
    if (text != null && !text.isBlank()) {
      content.add(TextBlock.builder().text(text).build());
    }
    if (attachments != null) {
      attachments.stream().map(this::dataBlock).forEach(content::add);
    }
    return Msg.builder().role(MsgRole.USER).content(content).build();
  }

  private DataBlock dataBlock(ChatAttachment attachment) {
    String url = attachment.url();
    io.agentscope.core.message.Source source;
    if (url.startsWith("data:")) {
      int comma = url.indexOf(',');
      if (comma < 0 || !url.substring(0, comma).endsWith(";base64")) {
        throw new IllegalArgumentException("Attachment data URL must be base64 encoded");
      }
      source = new Base64Source(attachment.mediaType(), url.substring(comma + 1));
    } else {
      source = new URLSource(url, attachment.mediaType());
    }
    String name =
        attachment.filename() == null || attachment.filename().isBlank()
            ? "image"
            : attachment.filename();
    return DataBlock.builder().source(source).name(name).build();
  }

  private String writeToolInput(Map<String, Object> input) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(input);
    } catch (Exception e) {
      throw new IllegalArgumentException("Tool input is not JSON serializable", e);
    }
  }

  private PermissionContextState allowRegisteredServerTools(Toolkit toolkit) {
    PermissionContextState.Builder permissions = PermissionContextState.builder();
    toolkit
        .getToolNames()
        .forEach(
            name ->
                permissions.addAllowRule(
                    name,
                    new PermissionRule(
                        name, null, PermissionBehavior.ALLOW, "datastoria-server-policy")));
    return permissions.build();
  }
}
