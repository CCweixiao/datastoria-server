package io.datastoria.server.api.compat;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import io.datastoria.server.api.error.PlainTextException;
import io.datastoria.server.dto.FeedbackAcceptedResponse;
import io.datastoria.server.dto.FeedbackUpsertRequest;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.FeedbackService;

import reactor.core.publisher.Mono;

/**
 * A10 — auto-explain feedback upsert. Body validation lives in {@link FeedbackService}; this
 * controller only translates parse failures into the Node-compat {@code Invalid JSON in request
 * body} plain-text error.
 *
 * <p>Status mapping: {@code 200} recorded, {@code 202} accepted-not-stored, {@code 400}
 * invalid-format (plain text), {@code 404} target-not-found (ProblemDetail, ADR-0003).
 */
@RestController
@RequestMapping("/api/ai/chat/feedback/auto-explain")
public class FeedbackController {

  private final FeedbackService feedbackService;
  private final ObjectMapper objectMapper;

  public FeedbackController(FeedbackService feedbackService, ObjectMapper objectMapper) {
    this.feedbackService = feedbackService;
    this.objectMapper = objectMapper;
  }

  @PostMapping
  public Mono<ResponseEntity<Object>> record(@RequestBody(required = false) JsonNode raw) {
    FeedbackUpsertRequest req = parse(raw);
    return IdentityContext.current()
        .flatMap(identity -> feedbackService.upsert(req, identity))
        .map(
            outcome -> {
              if (outcome.recorded()) {
                return ResponseEntity.status(HttpStatus.OK)
                    .body((Object) outcome.toRecordedResponse());
              }
              FeedbackAcceptedResponse body = outcome.toAcceptedResponse();
              return ResponseEntity.status(HttpStatus.ACCEPTED).body((Object) body);
            });
  }

  private FeedbackUpsertRequest parse(JsonNode raw) {
    if (raw == null || !raw.isObject()) {
      throw PlainTextException.badRequest("Invalid JSON in request body");
    }
    try {
      return objectMapper.treeToValue(raw, FeedbackUpsertRequest.class);
    } catch (MismatchedInputException e) {
      throw PlainTextException.badRequest("Invalid request format");
    } catch (IOException e) {
      throw PlainTextException.badRequest("Invalid JSON in request body");
    }
  }
}
