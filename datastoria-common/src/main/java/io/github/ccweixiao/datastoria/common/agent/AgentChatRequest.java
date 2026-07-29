package io.github.ccweixiao.datastoria.common.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parsed body of {@code POST /api/ai/agent} (Node A01 compatible, {@code validateRemoteChatRequest}
 * in {@code datastoria-web/src/lib/ai/session/remote-chat-request.ts}). Sub-objects with open shape
 * ({@code message}, {@code model}, {@code agentContext}) are kept as {@link JsonNode} so unknown
 * parts are preserved without a closed schema.
 *
 * <p><b>Supported in P4.6:</b> {@code sessionId}, {@code connectionId}, {@code message} (user text
 * extracted from {@code text} parts), {@code modelConfigId} or {@code model.provider} +{@code
 * model.modelId} (server resolves the tenant model config), {@code agentId} (optional; resolves the
 * published agent revision), {@code clientRequestId}/{@code Idempotency-Key} header (idempotency).
 * {@code agentContext} controls response language and reasoning at the server-owned model boundary.
 * Safe diagnostic fields from {@code context} are added to the server-owned system prompt. {@code
 * generateTitle} controls the independent model title call; {@code ephemeral} creates a temporary
 * session FK anchor that is removed when the one-off stream closes.
 *
 * <p><b>Forbidden</b> (rejected before this record is built): {@code model.apiKey}, {@code
 * connection.password}, any top-level {@code apiKey}. Legacy client-side {@code continuation:true}
 * is rejected because tool approvals/questions resume through the durable action/resume API.
 * ClickHouse tools are resolved and executed only by the Java AgentScope runtime.
 */
public record AgentChatRequest(
    String sessionId,
    String connectionId,
    JsonNode message,
    String modelConfigId,
    JsonNode model,
    String agentId,
    String clientRequestId,
    boolean continuation,
    boolean generateTitle,
    boolean ephemeral,
    JsonNode agentContext,
    JsonNode context) {

  /** The {@code message.id} (used as the SSE {@code start.messageId}); null when absent. */
  public String messageId() {
    return message != null && message.has("id") ? message.path("id").asText(null) : null;
  }

  /** The {@code message.role}. */
  public String role() {
    return message != null && message.has("role") ? message.path("role").asText("") : "";
  }

  /** Concatenated text from all {@code {type:"text"}} parts of {@code message.parts}, trimmed. */
  public String userText() {
    if (message == null) {
      return "";
    }
    JsonNode parts = message.path("parts");
    if (!parts.isArray()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (JsonNode part : parts) {
      if (part.isObject() && "text".equals(part.path("type").asText(""))) {
        String text = part.path("text").asText("");
        if (!text.isEmpty()) {
          if (sb.length() > 0) {
            sb.append('\n');
          }
          sb.append(text);
        }
      }
    }
    return sb.toString();
  }

  /** Provider key from {@code model.provider}, or null. */
  public String modelProvider() {
    return model != null ? model.path("provider").asText(null) : null;
  }

  /** Provider model id from {@code model.modelId}, or null. */
  public String modelId() {
    return model != null ? model.path("modelId").asText(null) : null;
  }
}
