package io.datastoria.server.api.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Marker base for P3 compat exceptions that MUST be serialised as a plain-text body to preserve
 * Node.js wire compatibility (see {@code docs/api/p3-openapi-extensions.yaml},
 * {@code PlainTextError} response).
 *
 * <p>Subclasses carry an {@link HttpStatus} and a fixed body string. The {@link
 * io.datastoria.server.api.GlobalExceptionHandler GlobalExceptionHandler} maps every {@code
 * PlainTextException} to {@code text/plain; charset=utf-8} with the configured status and body.
 *
 * <p>This is intentionally separate from the P2 ProblemDetail pathway ({@link NotFoundException}
 * et al.) so the two content types never collide.
 */
public class PlainTextException extends RuntimeException {

  private final HttpStatus status;
  private final String body;
  private final MediaType contentType;

  public PlainTextException(HttpStatus status, String body) {
    super(body);
    this.status = status;
    this.body = body;
    this.contentType = MediaType.TEXT_PLAIN;
  }

  public PlainTextException(HttpStatus status, String body, MediaType contentType) {
    super(body);
    this.status = status;
    this.body = body;
    this.contentType = contentType;
  }

  public HttpStatus status() {
    return status;
  }

  public String body() {
    return body;
  }

  public MediaType contentType() {
    return contentType;
  }

  /** HTTP 400 with a caller-visible body. */
  public static PlainTextException badRequest(String body) {
    return new PlainTextException(HttpStatus.BAD_REQUEST, body);
  }

  /** HTTP 401 with the literal body {@code Authentication required}. */
  public static PlainTextException authenticationRequired() {
    return new PlainTextException(HttpStatus.UNAUTHORIZED, "Authentication required");
  }

  /** HTTP 403 with the literal body {@code Invalid session share code}. */
  public static PlainTextException invalidShareCode() {
    return new PlainTextException(HttpStatus.FORBIDDEN, "Invalid session share code");
  }

  /** HTTP 404 with the literal body {@code Not found}. */
  public static PlainTextException notFound() {
    return new PlainTextException(HttpStatus.NOT_FOUND, "Not found");
  }

  /** HTTP 409 with the literal body {@code Session connectionId mismatch}. */
  public static PlainTextException connectionIdMismatch() {
    return new PlainTextException(HttpStatus.CONFLICT, "Session connectionId mismatch");
  }
}
