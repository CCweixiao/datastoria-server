package io.datastoria.server.agent.testing;

import java.util.Objects;

import io.datastoria.server.agent.runtime.ModelAdapter;
import io.datastoria.server.agent.runtime.ModelAdapterProvider;
import io.datastoria.server.domain.Model;

/**
 * Test-only {@link ModelAdapterProvider} backed by a swappable AgentScope model. Register as a bean
 * (overrides {@code NoOpModelAdapterProvider}); set the model per scenario. Reads no credential —
 * test models are network-free.
 */
public final class FakeModelAdapterProvider implements ModelAdapterProvider {

  private volatile io.agentscope.core.model.Model model;
  private volatile RuntimeException adapterFailure;

  public FakeModelAdapterProvider() {
    this.model = FakeStreamModel.builder().text("Hello").finish(1, 1).build();
  }

  public void setModel(io.agentscope.core.model.Model model) {
    this.model = Objects.requireNonNull(model);
  }

  public FakeStreamModel model() {
    return (FakeStreamModel) model;
  }

  public void reset() {
    this.model = FakeStreamModel.builder().text("Hello").finish(1, 1).build();
    this.adapterFailure = null;
  }

  public void failAdapterWith(RuntimeException failure) {
    this.adapterFailure = Objects.requireNonNull(failure);
  }

  @Override
  public ModelAdapter adapterFor(Model modelConfig) {
    if (adapterFailure != null) {
      throw adapterFailure;
    }
    return new FakeModelAdapter(model);
  }
}
