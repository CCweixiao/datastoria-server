package io.github.ccweixiao.datastoria.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.config.SessionShareConfig;
import io.github.ccweixiao.datastoria.common.domain.ChatMessage;
import io.github.ccweixiao.datastoria.common.domain.ChatSession;
import io.github.ccweixiao.datastoria.common.domain.SessionShare;
import io.github.ccweixiao.datastoria.common.dto.AppUIMessage;
import io.github.ccweixiao.datastoria.common.dto.ChatSessionDTO;
import io.github.ccweixiao.datastoria.common.dto.CreateSessionRequest;
import io.github.ccweixiao.datastoria.common.dto.CreateSessionResponse;
import io.github.ccweixiao.datastoria.common.dto.SessionPageDTO;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.error.SharePermissionDeniedException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ChatMessageRepository;
import io.github.ccweixiao.datastoria.dao.repository.ChatSessionRepository;
import io.github.ccweixiao.datastoria.dao.repository.SessionListCursor;
import io.github.ccweixiao.datastoria.dao.repository.SessionPage;
import io.github.ccweixiao.datastoria.dao.repository.SessionShareRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * A03–A07 chat session lifecycle. Every method touches JDBC inside {@link Mono#fromCallable}/{@link
 * Mono#fromRunnable} subscribed on {@code jdbcScheduler}.
 *
 * <p>Owner vs share-visitor access is resolved centrally by {@link #resolveAccess}; mutation routes
 * pass {@code writeRequired = true} so a share visitor is denied with {@code
 * SHARE_PERMISSION_DENIED} unless {@link SessionShareConfig#allowWrite()} is set (P3 compat window,
 * ADR-0001).
 */
@Service
public class SessionService {

  private static final String IMAGE_HISTORY_PLACEHOLDER =
      "[Image attachment omitted from saved history]";

  private static final Logger log = LoggerFactory.getLogger(SessionService.class);

  /** Default page size for A03 when no {@code limit} is supplied. */
  static final int DEFAULT_LIMIT = 100;

  /** Hard bounds for A03 {@code limit}. */
  static final int MIN_LIMIT = 1;

  static final int MAX_LIMIT = 500;

  /** Node-compatible default title when none is supplied on A04. */
  static final String DEFAULT_TITLE = "Inline error diagnosis";

  /** Permitted roles for initial-message parts on A04. */
  private static final java.util.Set<String> INITIAL_ROLES = java.util.Set.of("user", "assistant");

  private final ChatSessionRepository sessionRepo;
  private final ChatMessageRepository messageRepo;
  private final SessionShareRepository shareRepo;
  private final SessionShareService shareService;
  private final SessionShareConfig shareConfig;
  private final TransactionTemplate transactions;
  private final ObjectMapper objectMapper;
  private final Scheduler jdbcScheduler;
  private final SecureRandom random = new SecureRandom();

  public SessionService(
      ChatSessionRepository sessionRepo,
      ChatMessageRepository messageRepo,
      SessionShareRepository shareRepo,
      SessionShareService shareService,
      SessionShareConfig shareConfig,
      TransactionTemplate transactions,
      ObjectMapper objectMapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.sessionRepo = sessionRepo;
    this.messageRepo = messageRepo;
    this.shareRepo = shareRepo;
    this.shareService = shareService;
    this.shareConfig = shareConfig;
    this.transactions = transactions;
    this.objectMapper = objectMapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  // ============================================================ A03

  /** A03 — paginated session list scoped to the caller (share codes are NOT honoured). */
  public Mono<SessionPageDTO> list(
      String connectionId, String cursor, Integer limit, Identity identity) {
    return Mono.fromCallable(
            () -> {
              int safeLimit = parseLimit(limit);
              Optional<SessionListCursor> parsed = SessionListCursor.parse(cursor);
              if (parsed.isEmpty() && cursor != null && !cursor.isBlank()) {
                log.warn(
                    "malformed cursor ignored — returning page 1 (user={})", identity.userId());
              }
              SessionPage page =
                  sessionRepo.findPage(
                      identity.tenantId(),
                      identity.userId(),
                      connectionId,
                      parsed.orElse(null),
                      safeLimit);
              return SessionPageDTO.from(page.sessions(), page.nextCursor());
            })
        .subscribeOn(jdbcScheduler);
  }

  // ============================================================ A04

  /** A04 — idempotent create with optional initial messages (ADR-0002). */
  public Mono<CreateSessionResponse> create(CreateSessionRequest req, Identity identity) {
    return Mono.fromCallable(() -> transactions.execute(status -> doCreate(req, identity)))
        .subscribeOn(jdbcScheduler);
  }

  private CreateSessionResponse doCreate(CreateSessionRequest req, Identity identity) {
    validateCreateRequest(req);
    String title = resolveTitle(req.title());

    // Idempotent reuse: only kicks in when the caller supplied a sessionId.
    if (req.sessionId() != null && !req.sessionId().isBlank()) {
      String trimmedSessionId = req.sessionId().trim();
      Optional<ChatSession> existing =
          sessionRepo.findById(trimmedSessionId, identity.tenantId(), identity.userId());
      if (existing.isPresent()) {
        ChatSession owned = existing.get();
        if (!Objects.equals(owned.connectionId(), req.connectionId().trim())) {
          throw PlainTextException.connectionIdMismatch();
        }
        if (req.messages() != null && !req.messages().isEmpty()) {
          upsertInitialMessages(owned, req.messages(), identity);
        }
        return CreateSessionResponse.of(ChatSessionDTO.from(refresh(owned)));
      }
    }

    String sessionId = resolveSessionId(req.sessionId());
    ChatSession toSave =
        new ChatSession(
            sessionId,
            identity.tenantId(),
            identity.userId(),
            req.connectionId().trim(),
            title,
            0L,
            null,
            null);
    ChatSession saved;
    try {
      saved = sessionRepo.save(toSave);
    } catch (RuntimeException insertFailure) {
      // Two retries may race after both observed the id as absent. Treat the database unique
      // constraint as the serialization point, then apply the same idempotency checks as the
      // normal reuse path.
      Optional<ChatSession> raced =
          sessionRepo.findById(sessionId, identity.tenantId(), identity.userId());
      if (raced.isEmpty()) {
        throw insertFailure;
      }
      saved = raced.get();
      if (!Objects.equals(saved.connectionId(), req.connectionId().trim())) {
        throw PlainTextException.connectionIdMismatch();
      }
    }
    if (req.messages() != null && !req.messages().isEmpty()) {
      upsertInitialMessages(saved, req.messages(), identity);
    }
    return CreateSessionResponse.of(ChatSessionDTO.from(refresh(saved)));
  }

  // ============================================================ A05 / A06 / A07

  /** A05 — read a session (owner or share visitor). */
  public Mono<ChatSessionDTO> get(String sessionId, Identity identity, String shareCode) {
    return Mono.fromCallable(
            () ->
                ChatSessionDTO.from(resolveAccess(sessionId, identity, shareCode, false).session()))
        .subscribeOn(jdbcScheduler);
  }

  /** A06 — rename (owner only by default; share visitor denied unless {@code allow-write}). */
  public Mono<ChatSessionDTO> rename(
      String sessionId, String title, Identity identity, String shareCode) {
    return Mono.fromCallable(
            () -> {
              SessionAccess access = resolveAccess(sessionId, identity, shareCode, true);
              ChatSession renamed =
                  sessionRepo.rename(
                      access.session().id(),
                      access.session().tenantId(),
                      access.session().userId(),
                      title);
              return ChatSessionDTO.from(renamed);
            })
        .subscribeOn(jdbcScheduler);
  }

  /**
   * A07 — delete (owner only by default). Cascades messages/feedback via FK; share rows are marked
   * revoked (audit). All in a single transaction.
   */
  public Mono<Void> delete(String sessionId, Identity identity, String shareCode) {
    return Mono.<Void>fromRunnable(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      SessionAccess access = resolveAccess(sessionId, identity, shareCode, true);
                      String tenantId = access.session().tenantId();
                      String id = access.session().id();
                      String userId = access.session().userId();
                      // Revoke any active shares (no FK on ds_session_share; intentional audit
                      // row).
                      shareRepo.revoke(id, tenantId);
                      // Hard-delete the session; FKs cascade to messages and feedback.
                      sessionRepo.delete(id, tenantId, userId);
                    }))
        .subscribeOn(jdbcScheduler);
  }

  // ============================================================ shared access resolution

  /**
   * Resolves the caller's access to the session.
   *
   * <p>If a share code is present, the JWT verification path runs in {@link
   * SessionShareService#verify}; on any failure the visitor receives HTTP 403 {@code Invalid
   * session share code} (plain text). When {@code writeRequired} is true and the caller arrived via
   * a share, the caller must additionally pass the {@link SessionShareConfig#allowWrite()} gate;
   * otherwise HTTP 403 {@code SHARE_PERMISSION_DENIED} (ProblemDetail) is raised.
   *
   * <p>Owner flow: a missing session yields HTTP 404 {@code Not found} (plain text), matching
   * Node's behaviour and avoiding cross-tenant enumeration.
   */
  SessionAccess resolveAccess(
      String sessionId, Identity identity, String shareCode, boolean writeRequired) {
    if (shareCode != null && !shareCode.isBlank()) {
      SessionShareService.VerifiedShare verified = shareService.verify(shareCode, sessionId);
      if (writeRequired && !shareConfig.allowWrite()) {
        log.warn(
            "share_permission_denied session={} tenant={} share_row={}",
            sessionId,
            verified.tenantId(),
            verified.share().id());
        throw new SharePermissionDeniedException(
            "Share codes are read-only by default; see ADR-0001.");
      }
      return new SessionAccess(verified.session(), verified.share(), /* isShareVisitor */ true);
    }
    ChatSession session =
        sessionRepo
            .findById(sessionId, identity.tenantId(), identity.userId())
            .orElseThrow(PlainTextException::notFound);
    return new SessionAccess(session, null, /* isShareVisitor */ false);
  }

  // ============================================================ helpers

  /** Re-reads the session so createdAt/updatedAt/revision are populated by the DB. */
  private ChatSession refresh(ChatSession s) {
    return sessionRepo
        .findById(s.id(), s.tenantId(), s.userId())
        .orElseThrow(() -> new IllegalStateException("session not found after save: " + s.id()));
  }

  private int parseLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
      throw PlainTextException.badRequest("Invalid limit");
    }
    return limit;
  }

  private String resolveTitle(String title) {
    return (title == null || title.isBlank()) ? DEFAULT_TITLE : title;
  }

  /** 32-char hex time-ordered fallback when the caller omits sessionId (matches Node uuidv7). */
  private String resolveSessionId(String supplied) {
    if (supplied != null && !supplied.isBlank()) {
      return supplied.trim();
    }
    long nowMs = Instant.now().toEpochMilli();
    byte[] tail = new byte[8];
    random.nextBytes(tail);
    StringBuilder sb = new StringBuilder(32);
    sb.append(String.format("%016x", nowMs));
    for (byte b : tail) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  private void validateCreateRequest(CreateSessionRequest req) {
    if (req.connectionId() == null
        || req.connectionId().isBlank()
        || req.connectionId().length() > 255) {
      throw PlainTextException.badRequest("Invalid connectionId");
    }
    if (req.sessionId() != null) {
      String trimmed = req.sessionId().trim();
      if (trimmed.isEmpty() || trimmed.length() > 64) {
        throw PlainTextException.badRequest("Invalid sessionId");
      }
    }
    if (req.messages() != null) {
      for (AppUIMessage m : req.messages()) {
        if (m.id() == null || m.id().isBlank() || m.id().length() > 64) {
          throw PlainTextException.badRequest("Invalid messages");
        }
        if (m.role() == null || !INITIAL_ROLES.contains(m.role())) {
          throw PlainTextException.badRequest("Invalid messages");
        }
        if (m.parts() == null || !m.parts().isArray()) {
          throw PlainTextException.badRequest("Invalid messages");
        }
      }
    }
  }

  private void upsertInitialMessages(
      ChatSession session, java.util.List<AppUIMessage> messages, Identity identity) {
    long seq = 1L;
    for (AppUIMessage m : messages) {
      ChatMessage toSave =
          new ChatMessage(
              m.id(),
              session.tenantId(),
              session.id(),
              identity.userId(),
              m.role(),
              writeJson(sanitizePartsForPersistence(m.parts())),
              m.metadata() == null ? null : writeJson(m.metadata()),
              seq++,
              null,
              null);
      messageRepo.save(toSave);
    }
  }

  private String writeJson(JsonNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialise message parts", e);
    }
  }

  /**
   * Preserves the Node persistence contract without making the browser responsible for storage
   * safety. Image data URLs are request-only model inputs and must not be copied into chat history;
   * transient stream markers are likewise not replayable UI state.
   */
  private JsonNode sanitizePartsForPersistence(JsonNode parts) {
    ArrayNode sanitized = objectMapper.createArrayNode();
    boolean removedImage = false;
    for (JsonNode part : parts) {
      if (!part.isObject()) {
        sanitized.add(part.deepCopy());
        continue;
      }
      String type = part.path("type").asText("");
      if ("file".equals(type)
          && part.path("mediaType").asText("").startsWith("image/")
          && part.path("url").isTextual()) {
        removedImage = true;
        continue;
      }
      if ("step-start".equals(type)) {
        continue;
      }
      if ("reasoning".equals(type)
          && part.path("text").asText("").isBlank()
          && !part.path("providerMetadata").isObject()) {
        continue;
      }
      sanitized.add(part.deepCopy());
    }
    if (sanitized.isEmpty() && removedImage) {
      ObjectNode placeholder = sanitized.addObject();
      placeholder.put("type", "text");
      placeholder.put("text", IMAGE_HISTORY_PLACEHOLDER);
    }
    return sanitized;
  }

  /** Internal resolved-access tuple. */
  record SessionAccess(ChatSession session, SessionShare share, boolean shareVisitor) {

    public boolean isShareVisitor() {
      return shareVisitor;
    }
  }
}
