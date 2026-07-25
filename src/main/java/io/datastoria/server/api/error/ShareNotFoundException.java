package io.datastoria.server.api.error;

/**
 * Thrown by {@code POST /api/ai/sessions/{id}/share:revoke} when no active (non-revoked) share row
 * exists for the session. Mapped to {@code 404} RFC 9457 ProblemDetail with code {@code
 * SHARE_NOT_FOUND}; see ADR-0001.
 */
public class ShareNotFoundException extends RuntimeException {

  public ShareNotFoundException(String message) {
    super(message);
  }
}
