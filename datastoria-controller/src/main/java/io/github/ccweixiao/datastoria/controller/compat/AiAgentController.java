package io.github.ccweixiao.datastoria.controller.compat;

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

import io.github.ccweixiao.datastoria.agent.application.AgentEventReplayService;
import io.github.ccweixiao.datastoria.agent.application.AiSdkStreamEncoder;
import io.github.ccweixiao.datastoria.agent.application.ChatRunService;
import io.github.ccweixiao.datastoria.agent.application.SessionTitleService;
import io.github.ccweixiao.datastoria.common.agent.AgentChatRequest;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.ClientSecretNotAllowedException;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Java A01 chat endpoint — {@code POST /api/ai/agent}, compatible with the existing Node route and
 * the {@code @ai-sdk/react} frontend. Returns an AI SDK UI Message Stream (SSE) produced by {@link
 * AiSdkStreamEncoder}.
 *
 * <p>Compat-family controller (plain-text errors via {@link PlainTextException}); the one exception
 * is {@link ClientSecretNotAllowedException}, which yields a {@code 400 CLIENT_SECRET_NOT_ALLOWED}
 * ProblemDetail per {@code docs/api/http-api.md}.
 *
 * <p>Controller, encoder, and tests reference no {@code io.agentscope.*} type. The SSE body is the
 * single-use event stream; WebFlux subscribes to it once when writing the response (no manual
 * subscribe). Client disconnect propagates cancel upstream via the P4.2 {@code AgentRunService}
 * (dispose + interrupt).
 */
@RestController
@RequestMapping({"/api/ai/agent", "/api/ai/chat", "/api/ai/chat/v2"})
public class AiAgentController {

  private final ChatRunService service;
  private final SessionTitleService titleService;
  private final AgentEventReplayService replayService;

  public AiAgentController(
      ChatRunService service,
      SessionTitleService titleService,
      AgentEventReplayService replayService) {
    this.service = service;
    this.titleService = titleService;
    this.replayService = replayService;
  }

  @PostMapping
  public Mono<Void> chat(
      @RequestBody(required = false) JsonNode raw,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
      ServerWebExchange exchange) {
    markLegacyRoute(exchange);
    if (raw == null) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_JSON);
    }
    rejectClientSecrets(raw);
    AgentChatRequest req = parseRequest(raw, idempotencyKey);

    // Write the encoder's exact "data: {json}\n\n" frames as raw UTF-8 buffers. Going through a
    // message converter (e.g. returning Flux<String> with text/event-stream) would make WebFlux
    // re-encode each string as an SSE event (double "data:" framing); writing buffers directly
    // preserves the byte-exact AI SDK UI Message Stream the frontend expects.
    return IdentityContext.current()
        .flatMap(
            identity -> {
              if (lastEventId != null && !lastEventId.isBlank()) {
                if (req.clientRequestId() == null) {
                  throw PlainTextException.badRequest(
                      "Idempotency-Key is required for event replay");
                }
                long after = parseLastEventId(lastEventId);
                return writeSse(
                    exchange, replayService.replay(identity, req.clientRequestId(), after));
              }
              String fallbackTitle = provisionalTitle(req);
              Mono<String> generatedTitle = service.generateTitle(req, identity);
              if (generatedTitle == null) {
                generatedTitle =
                    Mono.empty(); // Mockito/default compatibility for controller tests.
              }
              Mono<String> resolvedTitle = generatedTitle;
              return service.stream(req, identity)
                  .flatMap(
                      events -> {
                        Flux<String> frames =
                            replayService.encodeAndRecord(
                                identity.tenantId(), events, resolvedTitle, fallbackTitle);
                        if (req.ephemeral()) {
                          frames =
                              frames
                                  .concatWith(
                                      service
                                          .cleanupEphemeral(req, identity)
                                          .thenMany(Flux.empty()))
                                  .doOnCancel(
                                      () -> service.cleanupEphemeral(req, identity).subscribe());
                        }
                        return writeSse(exchange, frames);
                      });
            });
  }

  private static void markLegacyRoute(ServerWebExchange exchange) {
    if ("/api/ai/chat".equals(exchange.getRequest().getPath().value())) {
      exchange.getResponse().getHeaders().set("Deprecation", "true");
      exchange
          .getResponse()
          .getHeaders()
          .set(HttpHeaders.LINK, "</api/ai/agent>; rel=\"successor-version\"");
    }
  }

  private static long parseLastEventId(String value) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed < 0) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (NumberFormatException ignored) {
      throw PlainTextException.badRequest("Last-Event-ID must be a non-negative integer");
    }
  }

  /**
   * Builds a provisional session title from the first words of the user message (mirrors Node A01's
   * {@code buildProvisionalTitle}). This synchronous fallback cannot hang; the independent
   * server-side model title has its own timeout and replaces it on the finish frame when available.
   * Derived only from the user's own text — no credential or provider error reaches it.
   */
  private String provisionalTitle(AgentChatRequest req) {
    try {
      return titleService.generateProvisional(req.userText(), req.generateTitle());
    } catch (RuntimeException ignored) {
      // Title is optional metadata. Its failure must never fail the primary answer.
      return null;
    }
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
    if (headerKey != null && bodyKey != null && !bodyKey.isBlank() && !headerKey.equals(bodyKey)) {
      throw PlainTextException.badRequest(
          "Idempotency-Key and clientRequestId must match when both are provided");
    }
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
        raw.get("agentContext"),
        raw.get("context"));
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
