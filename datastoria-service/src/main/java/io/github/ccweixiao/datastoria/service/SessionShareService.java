package io.github.ccweixiao.datastoria.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.config.SessionShareConfig;
import io.github.ccweixiao.datastoria.common.domain.ChatSession;
import io.github.ccweixiao.datastoria.common.domain.SessionShare;
import io.github.ccweixiao.datastoria.common.dto.ShareResponse;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.error.ShareNotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ChatSessionRepository;
import io.github.ccweixiao.datastoria.dao.repository.SessionShareRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Session share signing, verification and revocation (A09, A09b, and the share-code side of A05 /
 * A06 / A07 / A08). See {@code docs/adr/0001-session-share-permissions.md} for the security model.
 *
 * <p>The JWT is wire-compatible with Node's {@code session-share-code.ts} (same HS256 alg, same
 * {@code aud}/{@code scope} claims, same far-future default {@code exp}). Unlike Node, Java
 * additionally persists a row in {@code ds_session_share} keyed by the SHA-256 of the JWT so
 * revocation is possible without rotating the signing secret.
 */
@Service
public class SessionShareService {

  private static final Logger log = LoggerFactory.getLogger(SessionShareService.class);

  private final SessionShareRepository shares;
  private final ChatSessionRepository sessions;
  private final SessionShareConfig config;
  private final Scheduler jdbcScheduler;

  public SessionShareService(
      SessionShareRepository shares,
      ChatSessionRepository sessions,
      SessionShareConfig config,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.shares = shares;
    this.sessions = sessions;
    this.config = config;
    this.jdbcScheduler = jdbcScheduler;
  }

  /** Issue a share for the session. Owner-only. */
  public Mono<ShareResponse> issue(String sessionId, Identity owner) {
    return Mono.fromCallable(() -> doIssue(sessionId, owner)).subscribeOn(jdbcScheduler);
  }

  private ShareResponse doIssue(String sessionId, Identity owner) {
    ChatSession session = loadOwnedSession(sessionId, owner);
    Instant now = Instant.now();
    Instant expiresAt = config.defaultExpiresAt();
    String jwt = signJwt(session, owner, now, expiresAt);
    String tokenHash = sha256Hex(jwt);

    SessionShare row =
        new SessionShare(
            io.github.ccweixiao.datastoria.common.domain.Ulid.next(),
            session.tenantId(),
            session.id(),
            session.userId(),
            tokenHash,
            expiresAt,
            null,
            null);
    shares.issue(row);
    log.info(
        "session_share.issue session={} tenant={} owner={} expiresAt={}",
        session.id(),
        session.tenantId(),
        owner.userId(),
        expiresAt);
    String url = "/session/" + session.id() + "?code=" + jwt;
    return new ShareResponse(url, jwt, expiresAt);
  }

  /** Revoke the active share for the session. Owner-only. */
  public Mono<Void> revoke(String sessionId, Identity owner) {
    return Mono.<Void>fromRunnable(() -> doRevoke(sessionId, owner)).subscribeOn(jdbcScheduler);
  }

  private void doRevoke(String sessionId, Identity owner) {
    loadOwnedSession(sessionId, owner);
    Optional<SessionShare> active = shares.findActive(sessionId, owner.tenantId());
    if (active.isEmpty()) {
      throw new ShareNotFoundException("No active share for session " + sessionId);
    }
    int affected = shares.revoke(sessionId, owner.tenantId());
    if (affected == 0) {
      // Race: row was revoked between the find and the update.
      throw new ShareNotFoundException("No active share for session " + sessionId);
    }
    log.info(
        "session_share.revoke session={} tenant={} owner={}",
        sessionId,
        owner.tenantId(),
        owner.userId());
  }

  /**
   * Verify a share code presented by a visitor. Returns the resolved share row and the owned
   * session on success; throws {@link PlainTextException#invalidShareCode()} on any failure
   * (signature, audience, sub mismatch, exp past, row missing/revoked/expired, session missing).
   *
   * <p>The {@code sessionId} argument is the path parameter and MUST equal the JWT {@code sub}
   * claim; this prevents a share code for session A from being used to read session B.
   *
   * <p>Called from {@link SessionService#resolveAccess} which already runs on {@code
   * jdbcScheduler}; no extra {@code subscribeOn} is needed here.
   */
  public VerifiedShare verify(String code, String sessionId) {
    Claims claims;
    try {
      claims =
          Jwts.parser()
              .verifyWith(config.signingKey())
              .requireAudience(SessionShareConfig.AUDIENCE)
              .build()
              .parseSignedClaims(code)
              .getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("share code rejected: signature/parse failure (session={})", sessionId);
      throw PlainTextException.invalidShareCode();
    }
    if (!sessionId.equals(claims.getSubject())) {
      log.warn(
          "share code rejected: sub {} does not match path session {}",
          claims.getSubject(),
          sessionId);
      throw PlainTextException.invalidShareCode();
    }
    SessionShare row =
        shares
            .findByTokenHash(sha256Hex(code))
            .orElseThrow(
                () -> {
                  log.warn("share code rejected: no row for hash (session={})", sessionId);
                  return PlainTextException.invalidShareCode();
                });
    Instant now = Instant.now();
    if (row.revokedAt() != null) {
      log.warn("share code rejected: row revoked at {} (session={})", row.revokedAt(), sessionId);
      throw PlainTextException.invalidShareCode();
    }
    if (row.expiresAt() != null && row.expiresAt().isBefore(now)) {
      log.warn("share code rejected: row expired at {} (session={})", row.expiresAt(), sessionId);
      throw PlainTextException.invalidShareCode();
    }

    // Resolve the session via the share row's tenant+owner, NOT the visitor's identity.
    ChatSession session =
        sessions
            .findById(row.sessionId(), row.tenantId(), row.ownerUserId())
            .orElseThrow(
                () -> {
                  log.warn(
                      "share code rejected: session row missing (session={} tenant={})",
                      row.sessionId(),
                      row.tenantId());
                  return PlainTextException.invalidShareCode();
                });
    return new VerifiedShare(row, session);
  }

  /**
   * Sign an HS256 JWT mirroring Node's claims. A unique {@code jti} (ULID) is added so two shares
   * issued within the same second for the same session produce different JWTs and therefore
   * different SHA-256 hashes — the {@code (tenant_id, token_hash)} UNIQUE constraint would
   * otherwise fire on a rapid re-issue after revocation.
   */
  private String signJwt(ChatSession session, Identity owner, Instant now, Instant expiresAt) {
    Date iat = Date.from(now);
    Date exp = Date.from(expiresAt);
    return Jwts.builder()
        .id(io.github.ccweixiao.datastoria.common.domain.Ulid.next())
        .issuer(owner.userId())
        .subject(session.id())
        .audience()
        .add(SessionShareConfig.AUDIENCE)
        .and()
        .issuedAt(iat)
        .notBefore(iat)
        .expiration(exp)
        .claim("scope", SessionShareConfig.SCOPE)
        .signWith(config.signingKey(), Jwts.SIG.HS256)
        .compact();
  }

  /** Load the session scoped to the caller's identity; 404 if not owned. */
  private ChatSession loadOwnedSession(String sessionId, Identity owner) {
    return sessions
        .findById(sessionId, owner.tenantId(), owner.userId())
        .orElseThrow(PlainTextException::notFound);
  }

  /** Hex-encoded SHA-256 used as the {@code ds_session_share.token_hash} value. */
  static String sha256Hex(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }

  /** Result of a successful share-code verification; consumed by session/message services. */
  public record VerifiedShare(SessionShare share, ChatSession session) {

    public String tenantId() {
      return share.tenantId();
    }

    public String sessionId() {
      return session.id();
    }
  }

  /** Convenience: duration since epoch in seconds, exposed for tests. */
  static long toEpochSeconds(Instant instant) {
    return instant.getEpochSecond();
  }

  /** Convenience: now + ttl in seconds. */
  static Instant plusTtl(Instant now, long ttlSeconds) {
    return now.plus(Duration.ofSeconds(ttlSeconds));
  }
}
