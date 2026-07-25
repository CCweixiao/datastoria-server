package io.datastoria.server.agent.runtime;

import io.datastoria.server.domain.Model;

/**
 * Produces a {@link ModelAdapter} bound to a resolved tenant model configuration. This is the seam
 * where provider credentials are resolved <b>server-side</b> (provider config + decrypted secret)
 * and injected into the AgentScope {@code Model} boundary — never from the request body.
 *
 * <p>Lives in the runtime/adapter layer (the only place that may touch {@code io.agentscope.*}).
 * The controller and {@code ChatRunService} depend on this interface, not on any AgentScope type.
 *
 * <p>P4.6 ships a {@link NoOpModelAdapterProvider default} that fails fast because the real
 * provider adapter (reading the decrypted secret via {@code SecretService}) lands in P4.8; tests
 * inject a fake provider returning a deterministic, network-free model.
 */
public interface ModelAdapterProvider {

  /** Resolves provider credentials server-side and returns a {@link ModelAdapter} for the run. */
  ModelAdapter adapterFor(Model modelConfig);
}
