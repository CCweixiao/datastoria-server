package io.github.ccweixiao.datastoria.controller.compat;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import io.github.ccweixiao.datastoria.common.dto.CreateSessionRequest;
import io.github.ccweixiao.datastoria.common.dto.CreateSessionResponse;
import io.github.ccweixiao.datastoria.common.dto.SessionPageDTO;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.SessionService;

import reactor.core.publisher.Mono;

/**
 * P3 chat session lifecycle: A03 (list), A04 (create), A05 (read), A06 (rename), A07 (delete).
 *
 * <p>Routes preserve Node wire compatibility — error responses are emitted as {@code text/plain}
 * for the documented error cases (see {@link PlainTextException}) and {@code application/json} for
 * success bodies. Mutation routes that reach a share visitor return {@code
 * application/problem+json} with code {@code SHARE_PERMISSION_DENIED}.
 */
@RestController
@RequestMapping("/api/ai/chat/sessions")
public class ChatSessionController {

  private static final Logger log = LoggerFactory.getLogger(ChatSessionController.class);

  private final SessionService sessionService;
  private final ObjectMapper objectMapper;

  public ChatSessionController(SessionService sessionService, ObjectMapper objectMapper) {
    this.sessionService = sessionService;
    this.objectMapper = objectMapper;
  }

  // ============================================================ A03 list

  @GetMapping
  public Mono<ResponseEntity<SessionPageDTO>> list(
      @RequestParam(value = "connectionId", required = false) String connectionId,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false) Integer limit) {
    return IdentityContext.current()
        .flatMap(identity -> sessionService.list(connectionId, cursor, limit, identity))
        .map(ResponseEntity::ok);
  }

  // ============================================================ A04 create

  @PostMapping
  public Mono<ResponseEntity<CreateSessionResponse>> create(
      @RequestBody(required = false) JsonNode raw) {
    CreateSessionRequest req = parseCreateRequest(raw);
    return IdentityContext.current()
        .flatMap(identity -> sessionService.create(req, identity))
        .map(ResponseEntity::ok);
  }

  // ============================================================ A05 / A06 / A07

  @GetMapping("/{sessionId}")
  public Mono<ResponseEntity<Object>> get(
      @PathVariable String sessionId,
      @RequestHeader(value = "X-Session-Share-Code", required = false) String shareCode) {
    return IdentityContext.current()
        .flatMap(identity -> sessionService.get(sessionId, identity, shareCode))
        .map(ResponseEntity::ok);
  }

  @PatchMapping("/{sessionId}")
  public Mono<ResponseEntity<Object>> rename(
      @PathVariable String sessionId,
      @RequestHeader(value = "X-Session-Share-Code", required = false) String shareCode,
      @RequestBody(required = false) JsonNode raw) {
    String title = parseRenameTitle(raw);
    return IdentityContext.current()
        .flatMap(identity -> sessionService.rename(sessionId, title, identity, shareCode))
        .map(ResponseEntity::ok);
  }

  @DeleteMapping("/{sessionId}")
  public Mono<ResponseEntity<Void>> delete(
      @PathVariable String sessionId,
      @RequestHeader(value = "X-Session-Share-Code", required = false) String shareCode) {
    return IdentityContext.current()
        .flatMap(identity -> sessionService.delete(sessionId, identity, shareCode))
        .thenReturn(ResponseEntity.noContent().build());
  }

  // ============================================================ inline validation

  private CreateSessionRequest parseCreateRequest(JsonNode raw) {
    if (raw == null || !raw.isObject()) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_JSON);
    }
    try {
      return objectMapper.treeToValue(raw, CreateSessionRequest.class);
    } catch (MismatchedInputException e) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_JSON);
    } catch (IOException e) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_JSON);
    }
  }

  /**
   * Resolve the {@code title} field per A06 baseline: title must be a non-empty string after trim.
   * Anything else (missing, wrong type, empty after trim) yields HTTP 400 {@code Missing title}.
   * Body parse failures yield HTTP 400 {@code Invalid JSON in request body}.
   */
  private String parseRenameTitle(JsonNode raw) {
    if (raw == null || !raw.isObject()) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_JSON);
    }
    JsonNode titleNode = raw.get("title");
    if (titleNode == null || !titleNode.isTextual()) {
      throw PlainTextException.badRequest("Missing title");
    }
    String title = titleNode.asText();
    if (title == null || title.isBlank()) {
      throw PlainTextException.badRequest("Missing title");
    }
    return title.trim();
  }
}
