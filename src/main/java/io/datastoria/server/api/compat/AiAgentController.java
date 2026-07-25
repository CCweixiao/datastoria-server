package io.datastoria.server.api.compat;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.agent.application.AiSdkStreamEncoder;
import io.datastoria.server.agent.application.ChatRunService;
import io.datastoria.server.api.error.ClientSecretNotAllowedException;
import io.datastoria.server.api.error.PlainTextException;
import io.datastoria.server.identity.IdentityContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Java A01 chat endpoint — {@code POST /api/ai/agent}, compatible with the existing Node route and
 * the {@code @ai-sdk/react} frontend. Returns an AI SDK UI Message Stream (SSE) produced by {@link
 * AiSdkStreamEncoder}.
 *
 * <p>Compat-family controller (plain-text errors via {@link PlainTextException}); the one exception
 * is {@link ClientSecretNotAllowedException}, which yields a {@code 400 CLIENT_SECRET_NOT_ALLOWED}
 * ProblemDetail per docs/design/api-contracts.md §6.
 *
 * <p>Controller, encoder, and tests reference no {@code io.agentscope.*} type. The SSE body is the
 * single-use event stream; WebFlux subscribes to it once when writing the response (no manual
 * subscribe). Client disconnect propagates cancel upstream via the P4.2 {@code AgentRunService}
 * (dispose + interrupt).
 */
@RestController
@RequestMapping("/api/ai/agent")
public class AiAgentController {

  private final ChatRunService service;

  public AiAgentController(ChatRunService service) {
    this.service = service;
  }

  @PostMapping
  public Mono<Void> chat(
      @RequestBody(required = false) JsonNode raw,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      ServerWebExchange exchange) {
    if (raw == null) {
      throw PlainTextException.badRequest("Invalid JSON in request body");
    }
    rejectClientSecrets(raw);
    AgentChatRequest req = parseRequest(raw, idempotencyKey);

    // Write the encoder's exact "data: {json}\n\n" frames as raw UTF-8 buffers. Going through a
    // message converter (e.g. returning Flux<String> with text/event-stream) would make WebFlux
    // re-encode each string as an SSE event (double "data:" framing); writing buffers directly
    // preserves the byte-exact AI SDK UI Message Stream the frontend expects.
    return IdentityContext.current()
        .flatMap(identity -> service.stream(req, identity))
        .flatMap(events -> writeSse(exchange, AiSdkStreamEncoder.encode(events)));
  }

  /** Rejects any client-supplied credential before any processing or logging. */
  private static void rejectClientSecrets(JsonNode raw) {
    if (raw.has("apiKey") || raw.has("api_key")) {
      throw new ClientSecretNotAllowedException("apiKey");
    }
    JsonNode model = raw.get("model");
    if (model != null && model.isObject() && (model.has("apiKey") || model.has("api_key"))) {
      throw new ClientSecretNotAllowedException("model.apiKey");
    }
    JsonNode connection = raw.get("connection");
    if (connection != null
        && connection.isObject()
        && (connection.has("password") || connection.has("token"))) {
      throw new ClientSecretNotAllowedException("connection.password");
    }
  }

  private static AgentChatRequest parseRequest(JsonNode raw, String idempotencyKey) {
    String headerKey = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null;
    String bodyKey = raw.has("clientRequestId") ? raw.path("clientRequestId").asText(null) : null;
    if (headerKey == null && bodyKey != null && !bodyKey.isBlank()) {
      headerKey = bodyKey;
    }
    return new AgentChatRequest(
        text(raw, "sessionId"),
        text(raw, "connectionId"),
        raw.get("message"),
        text(raw, "modelConfigId"),
        raw.get("model"),
        text(raw, "agentId"),
        headerKey,
        raw.path("continuation").asBoolean(false),
        raw.path("generateTitle").asBoolean(true),
        raw.path("ephemeral").asBoolean(false),
        raw.get("agentContext"));
  }

  private static String text(JsonNode raw, String field) {
    JsonNode node = raw.get(field);
    if (node == null || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() ? null : value;
  }

  /** Sets the fixed SSE headers (stream-protocol.md §2) and streams the frames as raw buffers. */
  private static Mono<Void> writeSse(ServerWebExchange exchange, Flux<String> frames) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.OK);
    HttpHeaders headers = response.getHeaders();
    headers.setContentType(MediaType.TEXT_EVENT_STREAM);
    headers.add("Cache-Control", "no-cache");
    headers.add("Connection", "keep-alive");
    headers.add("X-Vercel-AI-UI-Message-Stream", "v1");
    headers.add("X-Accel-Buffering", "no");
    return response.writeWith(
        frames.map(frame -> response.bufferFactory().wrap(frame.getBytes(StandardCharsets.UTF_8))));
  }
}
