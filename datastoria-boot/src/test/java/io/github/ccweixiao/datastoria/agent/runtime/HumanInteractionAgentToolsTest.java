package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.agentscope.core.tool.ToolSuspendException;

class HumanInteractionAgentToolsTest {

  private final HumanInteractionAgentTools tools = new HumanInteractionAgentTools();

  @Test
  void oneQuestionSuspendsForServerSideResolution() {
    assertThatThrownBy(() -> tools.askUserQuestion(List.of(Map.of("question", "Which cluster?"))))
        .isInstanceOf(ToolSuspendException.class);
  }

  @Test
  void rejectsMissingOrMultipleQuestions() {
    assertThatThrownBy(() -> tools.askUserQuestion(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                tools.askUserQuestion(
                    List.of(Map.of("question", "One?"), Map.of("question", "Two?"))))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
