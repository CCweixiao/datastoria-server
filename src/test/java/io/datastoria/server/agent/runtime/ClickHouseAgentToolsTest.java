package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.agentscope.core.tool.Tool;

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
}
