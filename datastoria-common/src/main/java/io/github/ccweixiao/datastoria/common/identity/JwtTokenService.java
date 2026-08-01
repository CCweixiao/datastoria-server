package io.github.ccweixiao.datastoria.common.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.config.SecurityProperties;
import io.github.ccweixiao.datastoria.common.crypto.MasterKeyProvider;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

/**
 * Signs and verifies HS256 login JWTs. Uses a dedicated key resolved from {@code
 * datastoria.security.jwt.secret} (SHA-256 hashed to 32 bytes so any non-empty input yields a valid
 * HS256 key), falling back to the application master key. Login tokens carry a dedicated issuer and
 * audience so they can never be accepted by the session-share verifier (and vice versa).
 */
@Component
public class JwtTokenService {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

  private final SecurityProperties properties;
  private final SecretKey signingKey;

  public JwtTokenService(SecurityProperties properties, MasterKeyProvider masterKeyProvider) {
    this.properties = properties;
    this.signingKey = resolveSigningKey(properties.getJwt().getSecret(), masterKeyProvider);
  }

  public String sign(
      String userId, String tenantId, String role, String username, int tokenVersion) {
    Instant now = Instant.now();
    Instant exp = now.plus(Duration.ofMinutes(properties.getJwt().getTtlMinutes()));
    return Jwts.builder()
        .id(Ulid.next())
        .issuer(properties.getJwt().getIssuer())
        .audience()
        .add(properties.getJwt().getAudience())
        .and()
        .subject(userId)
        .claim("tenant", tenantId)
        .claim("role", role)
        .claim("name", username)
        .claim("ver", tokenVersion)
        .issuedAt(Date.from(now))
        .notBefore(Date.from(now))
        .expiration(Date.from(exp))
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();
  }

  /**
   * Verifies signature, issuer, audience and expiry. Returns empty on any failure so the caller can
   * uniformly reject with 401.
   */
  public Optional<VerifiedToken> parseAndVerify(String token) {
    try {
      Claims claims =
          Jwts.parser()
              .verifyWith(signingKey)
              .requireIssuer(properties.getJwt().getIssuer())
              .requireAudience(properties.getJwt().getAudience())
              .build()
              .parseSignedClaims(token)
              .getPayload();
      String subject = claims.getSubject();
      Object versionClaim = claims.get("ver");
      if (subject == null || subject.isBlank() || !(versionClaim instanceof Number version)) {
        return Optional.empty();
      }
      return Optional.of(
          new VerifiedToken(
              subject,
              stringClaim(claims, "tenant"),
              stringClaim(claims, "role"),
              stringClaim(claims, "name"),
              version.intValue()));
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("login token rejected: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private static String stringClaim(Claims claims, String name) {
    Object value = claims.get(name);
    return value == null ? null : String.valueOf(value);
  }

  private static SecretKey resolveSigningKey(String dedicatedSecret, MasterKeyProvider masterKey) {
    if (dedicatedSecret != null && !dedicatedSecret.isBlank()) {
      byte[] digest = sha256(dedicatedSecret.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(digest, "HmacSHA256");
    }
    return new SecretKeySpec(masterKey.activeKey().getEncoded(), "HmacSHA256");
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  /** Claims carried by a verified login token. */
  public record VerifiedToken(
      String userId, String tenantId, String role, String username, int tokenVersion) {}
}
