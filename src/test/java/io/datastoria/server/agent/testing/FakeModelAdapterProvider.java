package io.datastoria.server.agent.testing;

import java.util.Objects;

import io.datastoria.server.agent.runtime.ModelAdapter;
import io.datastoria.server.agent.runtime.ModelAdapterProvider;
import io.datastoria.server.domain.Model;

/**
 * Test-only {@link ModelAdapterProvider} backed by a swappable {@link FakeStreamModel}. Register as
 * a bean (overrides {@code NoOpModelAdapterProvider}); set the model per scenario. Reads no
 * credential — the fake model is network-free.
 */
public final class FakeModelAdapterProvider implements ModelAdapterProvider {

  private volatile FakeStreamModel model;

  public FakeModelAdapterProvider() {
    this.model = FakeStreamModel.builder().text("Hello").finish(1, 1).build();
  }

  public void setModel(FakeStreamModel model) {
    this.model = Objects.requireNonNull(model);
  }

  public void reset() {
    this.model = FakeStreamModel.builder().text("Hello").finish(1, 1).build();
  }

  @Override
  public ModelAdapter adapterFor(Model modelConfig) {
    return new FakeModelAdapter(model);
  }
}
