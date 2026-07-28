package io.github.ccweixiao.datastoria.agent.testing;

import io.agentscope.core.model.Model;
import io.github.ccweixiao.datastoria.agent.runtime.ModelAdapter;
import io.github.ccweixiao.datastoria.common.agent.RunContext;

/**
 * {@link ModelAdapter} that always returns the same (fake) {@link Model}. Used by P4.2 runtime
 * tests to drive the HarnessAgent with a deterministic, network-free model. The real provider
 * adapter lands in P4.6 and resolves credentials server-side; this one reads none.
 */
public final class FakeModelAdapter implements ModelAdapter {

  private final Model model;

  public FakeModelAdapter(Model model) {
    this.model = model;
  }

  @Override
  public Model modelFor(RunContext context) {
    return model;
  }
}
