package io.github.ccweixiao.datastoria.agent.runtime;

import io.agentscope.core.model.Model;
import io.github.ccweixiao.datastoria.common.agent.RunContext;

/**
 * Internal boundary over the AgentScope {@link Model}. The deterministic fake model (tests) and the
 * real provider (P4.6) share this boundary: a run asks for the model, the adapter resolves provider
 * configuration and credentials <em>server-side</em> and returns an AgentScope {@code Model} whose
 * {@code stream} reads any API key from {@code GenerateOptions.getApiKey()} — never from the
 * request body, never surfaced to the browser ({@code docs/security/secrets.md}).
 *
 * <p>Lives in the runtime/adapter layer: the only place AgentScope types may appear. Controller,
 * repository and the internal event model depend on {@link
 * io.github.ccweixiao.datastoria.common.agent domain} types only.
 */
public interface ModelAdapter {

  /** The AgentScope model boundary to use for this run. */
  Model modelFor(RunContext context);
}
