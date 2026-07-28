package io.github.ccweixiao.datastoria.common.agent;

/**
 * Sanitized failure category surfaced to clients via {@link AgentRunEvent.RunFailed}. Each code
 * maps to a fixed, safe user-facing message; raw provider/tool exception text is never emitted
 * (docs/design/harness-agent.md §11). New categories are added as P5+ tool failures appear.
 */
public enum RunFailureCode {
  MODEL_RATE_LIMITED("The model is busy. Please retry shortly."),
  MODEL_CONTEXT_TOO_LARGE("The conversation is too long for the selected model."),
  MODEL_UNAVAILABLE("The model could not be reached. Please retry."),
  AGENT_MAX_STEPS("The agent reached its maximum number of steps."),
  AGENT_INTERNAL("The agent run failed. Please retry.");

  private final String safeMessage;

  RunFailureCode(String safeMessage) {
    this.safeMessage = safeMessage;
  }

  /** Fixed, leak-free message safe to send to the browser. */
  public String safeMessage() {
    return safeMessage;
  }
}
