package io.github.ccweixiao.datastoria.agent.runtime;

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
  private static final List<String> WORKFLOW_TOOLS =
      List.of(
          "generate_sql",
          "optimize_sql",
          "generate_visualization",
          "search_file",
          "read_file",
          "ask_user_question");

  private final Set<String> availableToolNames;

  public AgentToolRegistry() {
    Toolkit catalog = new Toolkit();
    register(catalog, new ClickHouseAgentTools(null, null, null));
    register(catalog, new SqlWorkflowAgentTools(null, null, null));
    register(catalog, new RepositoryAgentTools(null));
    register(catalog, new HumanInteractionAgentTools());
    availableToolNames = Set.copyOf(catalog.getToolNames());
  }

  public Set<String> availableToolNames() {
    return availableToolNames;
  }

  public Toolkit createToolkit(List<Object> runTools) {
    Toolkit toolkit = new Toolkit();
    if (runTools != null) {
      runTools.forEach(tools -> register(toolkit, tools));
    }
    return toolkit;
  }

  public Toolkit createToolkit(Object runTools) {
    return createToolkit(runTools == null ? List.of() : List.of(runTools));
  }

  private static void register(Toolkit toolkit, Object tools) {
    ensureGroup(toolkit, READ_ONLY_GROUP, "Read-only ClickHouse discovery and SQL validation");
    ensureGroup(toolkit, EXTENDED_GROUP, "Extended ClickHouse analysis tools");
    ensureGroup(toolkit, "workflow", "SQL workflow, visualization, and repository inspection");
    toolkit.registerTool(tools);
    addExisting(toolkit, READ_ONLY_GROUP, READ_ONLY_TOOLS);
    addExisting(toolkit, EXTENDED_GROUP, EXTENDED_TOOLS);
    addExisting(toolkit, "workflow", WORKFLOW_TOOLS);
  }

  private static void ensureGroup(Toolkit toolkit, String name, String description) {
    if (toolkit.getToolGroup(name) == null) {
      toolkit.createToolGroup(name, description, true);
    }
  }

  private static void addExisting(Toolkit toolkit, String group, List<String> names) {
    names.stream()
        .filter(toolkit.getToolNames()::contains)
        .forEach(toolkit.getToolGroup(group)::addTool);
  }
}
