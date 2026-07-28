package io.github.ccweixiao.datastoria.service;

import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.FeedbackEvent;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.FeedbackAcceptedResponse;
import io.github.ccweixiao.datastoria.common.dto.FeedbackRecordedResponse;
import io.github.ccweixiao.datastoria.common.dto.FeedbackUpsertRequest;
import io.github.ccweixiao.datastoria.common.error.FeedbackTargetNotFoundException;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ChatMessageRepository;
import io.github.ccweixiao.datastoria.dao.repository.FeedbackEventRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * A10 — auto-explain feedback upsert.
 *
 * <p>Natural upsert key is {@code (tenantId, userId, source, sessionId, messageId)}; resubmit
 * overwrites every field. When {@code solved=true}, {@code reasonCode} and {@code freeText} are
 * normalised to {@code null}. When {@code solved=false}, {@code reasonCode} is required and must be
 * one of {@link #REASON_CODES} (mirrors Node's Zod {@code superRefine}).
 *
 * <p>The {@code messageId} must reference an existing message in the session — ADR-0003 makes the
 * "target not found" case return HTTP 404 instead of Node's HTTP 500.
 *
 * <p>The {@code 202 recorded:false} path is reserved for the prod profile when no remote store is
 * wired ({@code datastoria.feedback.store-enabled=false}); tests use the same flag.
 */
@Service
public class FeedbackService {

  private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

  static final String SOURCE = "auto_explain_error";

  static final Set<String> REASON_CODES =
      Set.of("wrong_diagnosis", "too_vague", "unsafe_fix", "missing_context", "other");

  private final FeedbackEventRepository feedbackRepo;
  private final ChatMessageRepository messageRepo;
  private final Scheduler jdbcScheduler;
  private final boolean storeEnabled;

  public FeedbackService(
      FeedbackEventRepository feedbackRepo,
      ChatMessageRepository messageRepo,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler,
      @Value("${datastoria.feedback.store-enabled:true}") boolean storeEnabled) {
    this.feedbackRepo = feedbackRepo;
    this.messageRepo = messageRepo;
    this.jdbcScheduler = jdbcScheduler;
    this.storeEnabled = storeEnabled;
  }

  /**
   * Records feedback. Returns either {@code 200} with {@link FeedbackRecordedResponse} or {@code
   * 202} with {@link FeedbackAcceptedResponse} when storage is disabled.
   */
  public Mono<RecordedOrAccepted> upsert(FeedbackUpsertRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              if (!storeEnabled) {
                log.info(
                    "feedback dropped (store-enabled=false) session={} message={}",
                    req.sessionId(),
                    req.messageId());
                return RecordedOrAccepted.accepted();
              }
              validate(req);
              if (!messageRepo.exists(
                  identity.tenantId(), identity.userId(), req.sessionId(), req.messageId())) {
                throw new FeedbackTargetNotFoundException(
                    "message not found: session="
                        + req.sessionId()
                        + " message="
                        + req.messageId());
              }
              FeedbackEvent event = buildEvent(req, identity);
              FeedbackEvent saved = feedbackRepo.upsert(event);
              return RecordedOrAccepted.recorded(saved.updatedAt(), saved.solved());
            })
        .subscribeOn(jdbcScheduler);
  }

  private void validate(FeedbackUpsertRequest req) {
    if (req.source() == null || !SOURCE.equals(req.source())) {
      throw PlainTextException.badRequest("Invalid request format");
    }
    if (req.sessionId() == null || req.sessionId().isBlank() || req.sessionId().length() > 64) {
      throw PlainTextException.badRequest("Invalid request format");
    }
    if (req.messageId() == null || req.messageId().isBlank() || req.messageId().length() > 255) {
      throw PlainTextException.badRequest("Invalid request format");
    }
    if (req.payload() == null
        || req.payload().queryId() == null
        || req.payload().queryId().isBlank()
        || req.payload().queryId().length() > 255) {
      throw PlainTextException.badRequest("Invalid request format");
    }
    if (req.payload().errorCode() != null && req.payload().errorCode().length() > 64) {
      throw PlainTextException.badRequest("Invalid request format");
    }
    if (req.payload().sql() != null && req.payload().sql().length() > 100_000) {
      throw PlainTextException.badRequest("Invalid request format");
    }
    if (req.freeText() != null && req.freeText().length() > 2000) {
      throw PlainTextException.badRequest("Invalid request format");
    }
    if (!req.solved()) {
      if (req.reasonCode() == null || !REASON_CODES.contains(req.reasonCode())) {
        throw PlainTextException.badRequest("Invalid request format");
      }
    }
  }

  private FeedbackEvent buildEvent(FeedbackUpsertRequest req, Identity identity) {
    boolean solved = req.solved();
    String reasonCode = solved ? null : req.reasonCode();
    String freeText = solved ? null : req.freeText();
    boolean recovery = req.recoveryActionTaken() != null && req.recoveryActionTaken();
    String payloadJson = serialisePayload(req);
    return new FeedbackEvent(
        Ulid.next(),
        identity.tenantId(),
        identity.userId(),
        SOURCE,
        req.sessionId(),
        req.messageId(),
        solved,
        reasonCode,
        payloadJson,
        freeText,
        recovery,
        null,
        null);
  }

  private String serialisePayload(FeedbackUpsertRequest req) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\"queryId\":");
    appendQuoted(sb, req.payload().queryId());
    if (req.payload().errorCode() != null) {
      sb.append(",\"errorCode\":");
      appendQuoted(sb, req.payload().errorCode());
    }
    if (req.payload().sql() != null) {
      sb.append(",\"sql\":");
      appendQuoted(sb, req.payload().sql());
    }
    sb.append('}');
    return sb.toString();
  }

  private static void appendQuoted(StringBuilder sb, String value) {
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        case '\b' -> sb.append("\\b");
        case '\f' -> sb.append("\\f");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append('"');
  }

  /** Either/or wrapper so the controller can emit the right status code. */
  public record RecordedOrAccepted(boolean recorded, Instant updatedAt, boolean solved) {

    public static RecordedOrAccepted recorded(Instant updatedAt, boolean solved) {
      return new RecordedOrAccepted(true, updatedAt, solved);
    }

    public static RecordedOrAccepted accepted() {
      return new RecordedOrAccepted(false, null, false);
    }

    public FeedbackRecordedResponse toRecordedResponse() {
      return FeedbackRecordedResponse.of(updatedAt, solved);
    }

    public FeedbackAcceptedResponse toAcceptedResponse() {
      return FeedbackAcceptedResponse.notStored();
    }
  }
}
