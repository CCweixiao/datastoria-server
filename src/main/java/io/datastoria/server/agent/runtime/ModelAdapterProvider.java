package io.datastoria.server.agent.runtime;

import io.datastoria.server.domain.Model;
import io.datastoria.server.identity.Identity;

/**
 * Produces a {@link ModelAdapter} bound to a resolved tenant model configuration. This is the seam
 * where provider credentials are resolved <b>server-side</b> (provider config + decrypted secret)
 * and injected into the AgentScope {@code Model} boundary — never from the request body.
 *
 * <p>Lives in the runtime/adapter layer (the only place that may touch {@code io.agentscope.*}).
 * The controller and {@code ChatRunService} depend on this interface, not on any AgentScope type.
 *
 * <p>The production implementation resolves encrypted API keys and user-scoped OAuth credentials
 * inside Java. Tests inject a fake provider returning a deterministic, network-free model.
 */
public interface ModelAdapterProvider {

  /** Resolves provider credentials server-side and returns a {@link ModelAdapter} for the run. */
  ModelAdapter adapterFor(Model modelConfig);

  /** Resolves credentials that are scoped to the authenticated user, such as OAuth tokens. */
  default ModelAdapter adapterFor(Model modelConfig, Identity identity) {
    return adapterFor(modelConfig);
  }
}
