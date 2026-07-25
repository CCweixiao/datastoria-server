package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.tool.Toolkit;

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
            "collect_rca_evidence",
            "generate_sql",
            "optimize_sql",
            "generate_visualization",
            "search_file",
            "read_file");
    var toolkit =
        registry.createToolkit(
            java.util.List.of(
                new ClickHouseAgentTools(null, null, null),
                new SqlWorkflowAgentTools(null, null, null),
                new RepositoryAgentTools(null)));
    assertThat(toolkit.getToolNames()).isEqualTo(registry.availableToolNames());
    assertThat(toolkit.getToolGroup(AgentToolRegistry.READ_ONLY_GROUP).getTools())
        .containsExactlyInAnyOrder("get_tables", "explore_schema", "validate_sql");
  }

  @Test
  void p7WorkflowAgentScopeInputsMatchSharedFrontendFixture() throws Exception {
    JsonNode fixture =
        new ObjectMapper()
            .readTree(Files.readString(Path.of("docs/fixtures/tools/p7-workflow-contract.json")));
    AgentToolRegistry registry = new AgentToolRegistry();
    Toolkit toolkit =
        registry.createToolkit(
            java.util.List.of(
                new SqlWorkflowAgentTools(null, null, null), new RepositoryAgentTools(null)));

    for (String name :
        java.util.List.of(
            "generate_sql", "optimize_sql", "generate_visualization", "search_file", "read_file")) {
      Map<String, Object> schema =
          toolkit.getToolSchemas().stream()
              .filter(tool -> name.equals(tool.getName()))
              .findFirst()
              .orElseThrow()
              .getParameters();
      @SuppressWarnings("unchecked")
      Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
      java.util.List<String> fixtureFields = new java.util.ArrayList<>();
      fixture.path(name).path("input").fieldNames().forEachRemaining(fixtureFields::add);
      assertThat(properties.keySet()).containsAll(fixtureFields);
    }
  }
}
