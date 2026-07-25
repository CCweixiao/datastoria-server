package io.datastoria.server.agent.runtime;

import java.util.List;
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

  static final String READ_ONLY_GROUP = "clickhouse-readonly";
  static final String EXTENDED_GROUP = "clickhouse-extended";
  private static final List<String> READ_ONLY_TOOLS =
      List.of("get_tables", "explore_schema", "validate_sql");
  private static final List<String> EXTENDED_TOOLS =
      List.of(
          "execute_sql",
          "collect_sql_optimization_evidence",
          "search_query_log",
          "collect_cluster_status",
          "collect_rca_evidence");

  private final Set<String> availableToolNames;

  public AgentToolRegistry() {
    Toolkit catalog = new Toolkit();
    register(catalog, new ClickHouseAgentTools(null, null, null));
    availableToolNames = Set.copyOf(catalog.getToolNames());
  }

  public Set<String> availableToolNames() {
    return availableToolNames;
  }

  public Toolkit createToolkit(Object runTools) {
    Toolkit toolkit = new Toolkit();
    if (runTools != null) {
      register(toolkit, runTools);
    }
    return toolkit;
  }

  private static void register(Toolkit toolkit, Object tools) {
    toolkit.createToolGroup(
        READ_ONLY_GROUP, "Read-only ClickHouse discovery and SQL validation", true);
    toolkit.createToolGroup(EXTENDED_GROUP, "Extended ClickHouse analysis tools", true);
    toolkit.registerTool(tools);
    READ_ONLY_TOOLS.forEach(toolkit.getToolGroup(READ_ONLY_GROUP)::addTool);
    EXTENDED_TOOLS.forEach(toolkit.getToolGroup(EXTENDED_GROUP)::addTool);
  }
}
