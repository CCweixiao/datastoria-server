package io.github.ccweixiao.datastoria.common.error;

/**
 * Thrown when a session-share visitor attempts a write operation (PATCH/DELETE/re-issue/revoke) and
 * the {@code datastoria.session-share.allow-write} flag is {@code false} (the default).
 *
 * <p>Mapped by {@link io.github.ccweixiao.datastoria.controller.GlobalExceptionHandler} to a {@code
 * 403} RFC 9457 ProblemDetail with code {@code SHARE_PERMISSION_DENIED}; see ADR-0001.
 */
public class SharePermissionDeniedException extends RuntimeException {

  public SharePermissionDeniedException(String message) {
    super(message);
  }
}
