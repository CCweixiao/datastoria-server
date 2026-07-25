package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.Toolkit;

class ClickHouseAgentToolsTest {

  @Test
  void exposesAllBrowserIndependentAgentScopeTools() {
    Set<String> names =
        Arrays.stream(ClickHouseAgentTools.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(Tool.class))
            .filter(java.util.Objects::nonNull)
            .map(Tool::name)
            .collect(Collectors.toSet());

    assertThat(names)
        .containsExactlyInAnyOrder(
            "execute_sql",
            "get_tables",
            "explore_schema",
            "validate_sql",
            "collect_sql_optimization_evidence",
            "search_query_log",
            "collect_cluster_status",
            "collect_rca_evidence");
  }

  @Test
  void p6ToolSchemasMatchFrontendInputContract() {
    Toolkit toolkit = new Toolkit();
    toolkit.registerTool(new ClickHouseAgentTools(null, null, null));

    Map<String, Object> getTables = schema(toolkit, "get_tables");
    assertThat(properties(getTables))
        .containsOnlyKeys("name_pattern", "database", "engine", "partition_key", "limit");
    assertThat(getTables.get("required")).isNull();

    Map<String, Object> exploreSchema = schema(toolkit, "explore_schema");
    assertThat(properties(exploreSchema)).containsOnlyKeys("tables");
    assertThat(exploreSchema.get("required")).isEqualTo(List.of("tables"));

    Map<String, Object> validateSql = schema(toolkit, "validate_sql");
    assertThat(properties(validateSql)).containsOnlyKeys("sql");
    assertThat(validateSql.get("required")).isEqualTo(List.of("sql"));
  }

  @Test
  void rejectsUnboundedDiscoveryAndInvalidQualifiedTableBeforeNetworkCall() {
    ClickHouseAgentTools tools = new ClickHouseAgentTools(null, null, null);

    assertThatThrownBy(() -> tools.getTables(null, null, null, null, null).block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one filter");
    assertThatThrownBy(
            () ->
                tools
                    .exploreSchema(
                        List.of(new ClickHouseAgentTools.SchemaTableRequest("events", List.of())))
                    .block())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("database.table");
  }

  private static Map<String, Object> schema(Toolkit toolkit, String name) {
    return toolkit.getToolSchemas().stream()
        .filter(schema -> name.equals(schema.getName()))
        .findFirst()
        .orElseThrow()
        .getParameters();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> properties(Map<String, Object> schema) {
    return (Map<String, Object>) schema.get("properties");
  }
}
