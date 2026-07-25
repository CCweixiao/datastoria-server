package io.datastoria.server.agent.runtime;

import io.datastoria.server.domain.Model;

/**
 * Default {@link ModelAdapterProvider} registered in the main application context so it loads
 * without a real provider. It fails fast on use: the real adapter (resolving the decrypted provider
 * secret via {@code SecretService} and building the AgentScope provider {@code Model}) lands in
 * P4.8. Tests register their own {@code @Primary} {@code FakeModelAdapterProvider}, which wins
 * injection over this plain candidate.
 */
public class NoOpModelAdapterProvider implements ModelAdapterProvider {

  @Override
  public ModelAdapter adapterFor(Model modelConfig) {
    throw new IllegalStateException(
        "Real model provider adapter is not configured (P4.8). Provide a ModelAdapterProvider bean.");
  }
}
