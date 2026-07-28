package io.github.ccweixiao.datastoria.agent.application;

import java.util.List;

/** AgentScope-free historical chat turn supplied to a new run. */
public record ChatTurn(
    String role,
    String text,
    List<ChatAttachment> attachments,
    List<ChatToolExchange> toolExchanges) {

  public ChatTurn(String role, String text) {
    this(role, text, List.of(), List.of());
  }

  public ChatTurn(String role, String text, List<ChatAttachment> attachments) {
    this(role, text, attachments, List.of());
  }

  public ChatTurn {
    attachments = attachments == null ? List.of() : List.copyOf(attachments);
    toolExchanges = toolExchanges == null ? List.of() : List.copyOf(toolExchanges);
  }
}
