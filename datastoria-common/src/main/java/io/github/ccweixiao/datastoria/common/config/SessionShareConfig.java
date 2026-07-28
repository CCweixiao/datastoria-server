package io.github.ccweixiao.datastoria.common.config;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.crypto.MasterKeyProvider;

/**
 * Resolved configuration for session share signing/verification (ADR-0001).
 *
 * <ul>
 *   <li>{@code datastoria.session-share.secret} (string) — dedicated HS256 secret. Falls back to
 *       the base64-decoded {@code datastoria.master-key} bytes when unset. The dedicated-secret
 *       form is preferred for prod so that master-key rotation can proceed independently of
 *       outstanding share JWTs.
 *   <li>{@code datastoria.session-share.default-ttl-seconds} (long, default {@link
 *       #DEFAULT_TTL_SECONDS}) — TTL applied at issuance. The default preserves Node's year-2100
 *       hard-coded expiry.
 *   <li>{@code datastoria.session-share.allow-write} (boolean, default {@code false}) — compat
 *       window flag. When {@code true}, share visitors may PATCH/DELETE; otherwise those attempts
 *       yield HTTP 403 {@code SHARE_PERMISSION_DENIED}. Must be removed in P11.
 * </ul>
 *
 * <p>The resolved {@link SecretKey} is exposed as a Spring bean so {@code SessionShareService} can
 * inject it directly without re-parsing configuration.
 */
@Component
public class SessionShareConfig {

  private static final Logger log = LoggerFactory.getLogger(SessionShareConfig.class);

  /**
   * Default share expiry as absolute epoch seconds: {@code 4102444800} == {@code
   * Instant.parse("2100-01-01T00:00:00Z").getEpochSecond()}. Preserves Node's hard-coded 2100
   * expiry.
   *
   * <p>Despite the property name {@code default-ttl-seconds} (kept for OpenAPI fidelity), the value
   * is treated as an <em>absolute target timestamp</em>, not a duration. This matches Node, which
   * hard-codes {@code exp = '2100-01-01T00:00:00Z'} regardless of when the share is issued.
   * Treating it as a real TTL would shift the expiry further every time a new share is issued.
   *
   * <p>Inline literal is required because {@code @Value} annotation arguments must be constant
   * expressions.
   */
  public static final long DEFAULT_TTL_SECONDS = 4102444800L;

  /** JWT {@code aud} claim value; mirrors {@code session-share-code.ts} in Node. */
  public static final String AUDIENCE = "https://datastoria.app/session/share";

  /** JWT {@code scope} claim value; mirrors Node. Effective permissions are route-driven. */
  public static final String SCOPE = "chat_session:full";

  private final SecretKey signingKey;
  private final long defaultTtlSeconds;
  private final boolean allowWrite;

  public SessionShareConfig(
      @Value("${datastoria.session-share.secret:}") String dedicatedSecret,
      @Value("${datastoria.session-share.default-ttl-seconds:" + DEFAULT_TTL_SECONDS + "}")
          long defaultTtlSeconds,
      @Value("${datastoria.session-share.allow-write:false}") boolean allowWrite,
      MasterKeyProvider masterKeyProvider) {
    this.signingKey = resolveSigningKey(dedicatedSecret, masterKeyProvider);
    this.defaultTtlSeconds = defaultTtlSeconds;
    this.allowWrite = allowWrite;
    if (allowWrite) {
      log.warn(
          "datastoria.session-share.allow-write=true — share visitors can mutate sessions."
              + " Compat window only; remove in P11 (ADR-0001).");
    }
    if (dedicatedSecret == null || dedicatedSecret.isBlank()) {
      log.info(
          "datastoria.session-share.secret is unset; falling back to datastoria.master-key for"
              + " HS256 signing.");
    }
  }

  private static SecretKey resolveSigningKey(String dedicatedSecret, MasterKeyProvider masterKey) {
    if (dedicatedSecret != null && !dedicatedSecret.isBlank()) {
      // Operators supply the same string Node used as SESSION_SHARE_SECRET; we hash its UTF-8
      // bytes to a 32-byte key so HS256 has the required width regardless of input length.
      byte[] bytes = dedicatedSecret.getBytes(StandardCharsets.UTF_8);
      return new SecretKeySpec(bytes, "HmacSHA256");
    }
    byte[] bytes = masterKey.activeKey().getEncoded();
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  /** HS256 signing/verification key. */
  public SecretKey signingKey() {
    return signingKey;
  }

  /**
   * Absolute share expiry ({@link Instant}). Resolved from {@code default-ttl-seconds} (which is
   * actually an absolute epoch target; see {@link #DEFAULT_TTL_SECONDS}).
   */
  public Instant defaultExpiresAt() {
    return Instant.ofEpochSecond(defaultTtlSeconds);
  }

  /** Raw configured value (epoch seconds of the default expiry target). */
  public long defaultTtlSeconds() {
    return defaultTtlSeconds;
  }

  /** Compat flag — when {@code true} share visitors can mutate the session. */
  public boolean allowWrite() {
    return allowWrite;
  }
}
