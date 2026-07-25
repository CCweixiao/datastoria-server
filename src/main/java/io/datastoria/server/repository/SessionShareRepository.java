package io.datastoria.server.repository;

import java.util.Optional;

import io.datastoria.server.domain.SessionShare;

/**
 * Persisted session share rows. Stores only the SHA-256 hash of the share JWT so revocation is
 * possible; see ADR-0001.
 */
public interface SessionShareRepository {

  /**
   * Inserts a new active share row. The caller MUST verify via {@link #findActive} first that no
   * active share exists for the session, otherwise the V4 UNIQUE (tenant, session, active_key)
   * constraint fires.
   */
  SessionShare issue(SessionShare share);

  /** Returns the active (non-revoked) share for a session, or empty if none. */
  Optional<SessionShare> findActive(String sessionId, String tenantId);

  /** Looks up a share row by token hash. Used during verification. */
  Optional<SessionShare> findByTokenHash(String tokenHash, String tenantId);

  /**
   * Marks the active share for the session as revoked. Returns the number of rows affected
   * (0 when no active share exists, 1 on success).
   */
  int revoke(String sessionId, String tenantId);
}
