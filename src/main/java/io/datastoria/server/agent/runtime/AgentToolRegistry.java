package io.datastoria.server.agent.runtime;

import java.util.Set;

import org.springframework.stereotype.Component;

import io.agentscope.core.tool.Toolkit;

/**
 * Single registration source for server-side AgentScope tools.
 *
 * <p>Skill availability and each run's model-boundary Toolkit are derived through AgentScope's own
 * registration logic, so adding or renaming a {@code @Tool} method cannot leave a duplicated
 * hand-maintained availability list stale.
 */
@Component
public class AgentToolRegistry {

  private final Set<String> availableToolNames;

  public AgentToolRegistry() {
    Toolkit catalog = new Toolkit();
    catalog.registerTool(new ClickHouseAgentTools(null, null, null));
    availableToolNames = Set.copyOf(catalog.getToolNames());
  }

  public Set<String> availableToolNames() {
    return availableToolNames;
  }

  public Toolkit createToolkit(Object runTools) {
    Toolkit toolkit = new Toolkit();
    if (runTools != null) {
      toolkit.registerTool(runTools);
    }
    return toolkit;
  }
}
