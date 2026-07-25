package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {

  @Test
  void availabilityCatalogComesFromAgentScopeToolkitRegistration() {
    AgentToolRegistry registry = new AgentToolRegistry();

    assertThat(registry.availableToolNames())
        .containsExactlyInAnyOrder(
            "execute_sql",
            "get_tables",
            "explore_schema",
            "validate_sql",
            "collect_sql_optimization_evidence",
            "search_query_log",
            "collect_cluster_status",
            "collect_rca_evidence");
    assertThat(registry.createToolkit(new ClickHouseAgentTools(null, null, null)).getToolNames())
        .isEqualTo(registry.availableToolNames());
  }
}
