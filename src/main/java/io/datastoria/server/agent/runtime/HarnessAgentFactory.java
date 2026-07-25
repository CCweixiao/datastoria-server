package io.datastoria.server.agent.runtime;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.harness.agent.HarnessAgent;
import io.datastoria.server.agent.application.ChatTurn;
import io.datastoria.server.agent.domain.RunContext;

/**
 * Builds a run-scoped {@link RunnableAgent} backed by a minimal-permission {@link HarnessAgent}.
 *
 * <p>P4 freezes the HarnessAgent configuration mandated by ADR-0004 §3.5 and
 * docs/design/harness-agent.md §4: every P5+ capability (filesystem, shell, memory, skills,
 * subagents, workspace context, tools config) is explicitly disabled, and the residual {@code
 * wait_async_results} helper that AgentScope 2.0.0 registers regardless is removed, so the
 * model-boundary tool schema stays empty. No workspace directory is required with every capability
 * off (verified against the 2.0.0 jar), so P4.2 performs no per-run filesystem I/O.
 *
 * <p>AgentScope's {@code AgentTraceMiddleware} is also disabled ({@code
 * enableAgentTracingLog(false)}): it logs model output and raw exception {@code toString()} at
 * INFO, which could echo prompt/context fragments or credential-bearing provider errors. Disabling
 * it does not affect error handling — stream errors are still explicitly consumed into {@code
 * RunFailed} downstream; DataStoria's own redacted observability (docs/design/harness-agent.md §12)
 * replaces this verbose trace.
 *
 * <p>The user message is normalized to a plain AgentScope {@code Msg} here. Richer UIMessage
 * normalization (image/tool-result parts) arrives in P4.6; P4 is text-only.
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
    Toolkit toolkit = toolRegistry.createToolkit(capabilities.tools());
    PermissionContextState permissionContext =
        capabilities.permissionContext() == null
            ? allowRegisteredServerTools(toolkit)
            : capabilities.permissionContext();
    HarnessAgent.Builder builder =
        HarnessAgent.builder()
            .name("run-" + context.runId())
            .sysPrompt(config.systemPrompt())
            .model(modelAdapter.modelFor(context))
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
            .disableCompaction()
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
    // AgentScope 2.0.0 registers this async helper even with every optional capability off. P4 has
    // no async tools, so strip it to keep the model boundary tool-free (ADR-0004 §3.5).
    agent.getToolkit().removeTool("wait_async_results");

    List<Msg> messages = new ArrayList<>();
    for (ChatTurn turn : history) {
      MsgRole role = "assistant".equals(turn.role()) ? MsgRole.ASSISTANT : MsgRole.USER;
      messages.add(Msg.builder().role(role).textContent(turn.text()).build());
    }
    messages.add(Msg.builder().role(MsgRole.USER).textContent(userText).build());
    return new HarnessRunnableAgent(
        context.runId(),
        agent,
        messages,
        new AgentEventMapper(context, clock),
        RuntimeContext.builder()
            .userId(context.userId())
            // AgentScope state is run-scoped. Chat history is reconstructed by DataStoria.
            .sessionId(context.runId())
            .build());
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
