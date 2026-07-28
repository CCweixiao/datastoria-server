package io.github.ccweixiao.datastoria.controller.compat;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import io.github.ccweixiao.datastoria.common.domain.FeedbackEvent;
import io.github.ccweixiao.datastoria.common.dto.FeedbackAcceptedResponse;
import io.github.ccweixiao.datastoria.common.dto.FeedbackUpsertRequest;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.dao.repository.FeedbackEventRepository;
import io.github.ccweixiao.datastoria.service.FeedbackService;

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
@RequestMapping("/api/ai/chat/feedback")
public class FeedbackController {

  private final FeedbackService feedbackService;
  private final ObjectMapper objectMapper;
  private final FeedbackEventRepository feedbackRepository;

  public FeedbackController(
      FeedbackService feedbackService,
      ObjectMapper objectMapper,
      FeedbackEventRepository feedbackRepository) {
    this.feedbackService = feedbackService;
    this.objectMapper = objectMapper;
    this.feedbackRepository = feedbackRepository;
  }

  @GetMapping("/report")
  public Mono<Map<String, Object>> report(
      @RequestParam(required = false) String source, @RequestParam(required = false) Integer days) {
    if (days != null && (days < 1 || days > 3650)) {
      throw PlainTextException.badRequest("Invalid days filter");
    }
    return IdentityContext.current()
        .map(
            identity -> {
              if (!identity.isAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
              }
              Instant after = days == null ? null : Instant.now().minus(days, ChronoUnit.DAYS);
              List<FeedbackEvent> events =
                  feedbackRepository.findForReport(identity.tenantId(), source, after);
              Map<String, Object> filters = new LinkedHashMap<>();
              if (source != null) {
                filters.put("source", source);
              }
              if (days != null) {
                filters.put("days", days);
              }
              return Map.of("filters", filters, "report", buildReport(events));
            });
  }

  private Map<String, Object> buildReport(List<FeedbackEvent> events) {
    long solved = events.stream().filter(FeedbackEvent::solved).count();
    Map<String, Long> reasons = new LinkedHashMap<>();
    Map<String, Long> errors = new LinkedHashMap<>();
    for (FeedbackEvent event : events) {
      if (!event.solved() && event.reasonCode() != null) {
        reasons.merge(event.reasonCode(), 1L, Long::sum);
      }
      try {
        JsonNode code = objectMapper.readTree(event.payloadJson()).get("errorCode");
        if (code != null && !code.isNull() && !code.asText().isBlank()) {
          errors.merge(code.asText(), 1L, Long::sum);
        }
      } catch (IOException ignored) {
        // Malformed historical payloads do not make the aggregate unavailable.
      }
    }
    return Map.of(
        "totalFeedback", events.size(),
        "solvedCount", solved,
        "solvedRate", events.isEmpty() ? 0 : Math.round(solved * 100.0 / events.size()),
        "topErrorCodes", points(errors, 5),
        "negativeReasons", points(reasons, Integer.MAX_VALUE));
  }

  private List<Map<String, Object>> points(Map<String, Long> values, int limit) {
    return values.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(limit)
        .map(entry -> Map.<String, Object>of("label", entry.getKey(), "count", entry.getValue()))
        .toList();
  }

  @PostMapping("/auto-explain")
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
