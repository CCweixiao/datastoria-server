package io.github.ccweixiao.datastoria.agent.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;

import io.agentscope.core.shutdown.GracefulShutdownManager;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;

/**
 * Test-only factory helpers; production wiring always supplies MysqlAgentStateStore. The harness
 * settings use a per-factory temp data directory so tests never touch the real {@code
 * ~/.datastoria.agent}.
 */
public final class TestHarnessAgentFactories {

  private TestHarnessAgentFactories() {}

  public static HarnessAgentFactory create() {
    return create(Clock.systemUTC());
  }

  public static HarnessAgentFactory create(Clock clock) {
    return create(clock, new InMemoryAgentStateStore());
  }

  public static HarnessAgentFactory create(AgentStateStore stateStore) {
    return create(Clock.systemUTC(), stateStore);
  }

  private static HarnessAgentFactory create(Clock clock, AgentStateStore stateStore) {
    try {
      return new HarnessAgentFactory(
          clock,
          new AgentToolRegistry(),
          stateStore,
          new AgentHarnessSettings(
              Files.createTempDirectory("datastoria-agent-test"), 25, 32_768, 0.8, 100_000, 20),
          GracefulShutdownManager.getInstance());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create test agent data directory", e);
    }
  }
}
