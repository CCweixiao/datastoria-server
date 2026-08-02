package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.agentscope.core.tool.Tool;

class ApprovalAgentToolsTest {

  @Test
  void exposesOnlyDraftSubmissionAndStatusTools() {
    Map<String, Tool> tools =
        Arrays.stream(ApprovalAgentTools.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(Tool.class))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toMap(Tool::name, Function.identity()));

    assertThat(tools.keySet())
        .containsExactlyInAnyOrder(
            "list_approval_work_order_types",
            "prepare_ddl_approval",
            "submit_ddl_approval",
            "get_approval_status");
    assertThat(tools.get("list_approval_work_order_types").readOnly()).isTrue();
    assertThat(tools.get("get_approval_status").readOnly()).isTrue();
    assertThat(tools.get("prepare_ddl_approval").readOnly()).isFalse();
    assertThat(tools.get("submit_ddl_approval").readOnly()).isFalse();
    assertThat(tools.keySet())
        .doesNotContain("approve_work_order", "execute_ddl_work_order", "retry_ddl");
  }
}
