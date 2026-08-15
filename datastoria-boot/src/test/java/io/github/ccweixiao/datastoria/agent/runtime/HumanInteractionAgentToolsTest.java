package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.agentscope.core.tool.ToolSuspendException;

class HumanInteractionAgentToolsTest {

  private final HumanInteractionAgentTools tools = new HumanInteractionAgentTools();

  @Test
  void oneQuestionSuspendsForServerSideResolution() {
    assertThatThrownBy(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "header",
                            "Which cluster?",
                            "options",
                            List.of(Map.of("id", "o1", "label", "Prod", "input", "none"))))))
        .isInstanceOf(ToolSuspendException.class);
  }

  @Test
  void compactProviderShapesNormalizeAndSuspend() {
    // {question, options: string[]}
    assertThatCode(
            () ->
                tools.askUserQuestion(
                    List.of(Map.of("question", "下一步？", "options", List.of("查看", "跳过")))))
        .isInstanceOf(ToolSuspendException.class);
    // {question, choices: [{label, description}]}
    assertThatCode(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "question",
                            "检查方向？",
                            "choices",
                            List.of(
                                Map.of("label", "Schema", "description", "检查表结构"),
                                Map.of("label", "监控"))))))
        .isInstanceOf(ToolSuspendException.class);
    // {question, options: [{label, description}]}
    assertThatCode(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "question",
                            "表结构？",
                            "options",
                            List.of(Map.of("label", "我来描述", "description", "自定义字段"))))))
        .isInstanceOf(ToolSuspendException.class);
  }

  @Test
  void rejectsMissingOrMultipleQuestions() {
    assertThatThrownBy(() -> tools.askUserQuestion(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "header",
                            "One?",
                            "options",
                            List.of(Map.of("id", "o1", "label", "A", "input", "none"))),
                        Map.of(
                            "header",
                            "Two?",
                            "options",
                            List.of(Map.of("id", "o1", "label", "A", "input", "none"))))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnrecognizedQuestionShapeWithCorrectiveMessage() {
    assertThatThrownBy(() -> tools.askUserQuestion(List.of(Map.of("question", "No options?"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unrecognized question shape");
  }

  @Test
  void rejectsUnknownFieldsWithAllowedList() {
    assertThatThrownBy(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "header",
                            "Which?",
                            "surprise",
                            "x",
                            "options",
                            List.of(Map.of("id", "o1", "label", "A", "input", "none"))))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown question field \"surprise\"");
  }

  @Test
  void rejectsDuplicateOptionIdsAndBadInputModes() {
    assertThatThrownBy(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "header",
                            "Which?",
                            "options",
                            List.of(
                                Map.of("id", "o1", "label", "A", "input", "none"),
                                Map.of("id", "o1", "label", "B", "input", "none"))))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
    assertThatThrownBy(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "header",
                            "Which?",
                            "options",
                            List.of(Map.of("id", "o1", "label", "A", "input", "voice"))))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("option.input");
  }

  @Test
  void selectOptionsRequireNonEmptyStringChoices() {
    assertThatThrownBy(
            () ->
                tools.askUserQuestion(
                    List.of(
                        Map.of(
                            "header",
                            "Which metric?",
                            "options",
                            List.of(Map.of("id", "o1", "label", "Metric", "input", "select"))))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("choices");
  }
}
