package io.github.ccweixiao.datastoria.agent.runtime;

import java.time.Clock;

import io.agentscope.core.state.InMemoryAgentStateStore;

/** Test-only factory helpers; production wiring always supplies MysqlAgentStateStore. */
public final class TestHarnessAgentFactories {

  private TestHarnessAgentFactories() {}

  public static HarnessAgentFactory create() {
    return create(Clock.systemUTC());
  }

  public static HarnessAgentFactory create(Clock clock) {
    return new HarnessAgentFactory(clock, new AgentToolRegistry(), new InMemoryAgentStateStore());
  }
}
