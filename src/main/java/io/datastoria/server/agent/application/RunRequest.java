package io.datastoria.server.agent.application;

import java.util.List;
import java.util.Objects;

import io.datastoria.server.agent.domain.RunContext;
import io.datastoria.server.agent.runtime.AgentRuntimeConfig;
import io.datastoria.server.agent.runtime.ModelAdapter;

/**
 * Internal request to start an agent run. Carries the resolved {@link RunContext}, the {@link
 * ModelAdapter} (fake in tests, provider-resolving in P4.6), the pinned {@link AgentRuntimeConfig},
 * and the user's text. In P4.6 the service resolves the adapter and config from repositories; P4.2
 * takes them directly so the runtime can be exercised with a fake model and no network.
 */
public record RunRequest(
    RunContext context,
    ModelAdapter modelAdapter,
    AgentRuntimeConfig config,
    List<ChatTurn> history,
    String userText) {

  public RunRequest(
      RunContext context, ModelAdapter modelAdapter, AgentRuntimeConfig config, String userText) {
    this(context, modelAdapter, config, List.of(), userText);
  }

  public RunRequest {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(modelAdapter, "modelAdapter");
    Objects.requireNonNull(config, "config");
    history = history == null ? List.of() : List.copyOf(history);
    userText = userText == null ? "" : userText;
  }
}
