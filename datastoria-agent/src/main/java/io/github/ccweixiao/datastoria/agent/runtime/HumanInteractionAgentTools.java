package io.github.ccweixiao.datastoria.agent.runtime;

import java.util.List;
import java.util.Map;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.ToolSuspendException;

/** Server-side HITL tools. They suspend AgentScope; the browser only resolves durable actions. */
public final class HumanInteractionAgentTools {

  @Tool(
      name = "ask_user_question",
      description = "Ask exactly one structured follow-up question and wait for the user's answer.",
      readOnly = true)
  public String askUserQuestion(
      @ToolParam(
              name = "questions",
              description =
                  "Exactly one question object: { \"header\": short title, \"options\": [ "
                      + "{ \"id\": \"o1\", \"label\": \"short choice\", \"input\": \"none\" } ] }. "
                      + "Set \"input\" to \"text\" when the user must type a free-form answer, "
                      + "\"select\" with a \"choices\" string array for a fixed set, or \"none\" for "
                      + "a single click. Emit only these fields; do not invent alternatives such as "
                      + "{ label, description }.")
          List<Map<String, Object>> questions) {
    if (questions == null || questions.size() != 1) {
      throw new IllegalArgumentException("ask_user_question requires exactly one question");
    }
    throw new ToolSuspendException("Waiting for user response");
  }
}
