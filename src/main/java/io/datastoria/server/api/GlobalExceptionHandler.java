package io.datastoria.server.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.datastoria.server.api.error.ClientSecretNotAllowedException;
import io.datastoria.server.api.error.FeedbackTargetNotFoundException;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.PlainTextException;
import io.datastoria.server.api.error.ProviderOperationException;
import io.datastoria.server.api.error.ResourceInUseException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.api.error.ShareNotFoundException;
import io.datastoria.server.api.error.SharePermissionDeniedException;

/**
 * Translates application exceptions into RFC 9457 {@link org.springframework.http.ProblemDetail}
 * responses. No secret value (cipher text or plaintext) is ever placed in the response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemDetailFactory problems;

  public GlobalExceptionHandler(ProblemDetailFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleNotFound(
      NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problems.forStatus(404, "NOT_FOUND", "Resource not found", safeMessage(ex)));
  }

  @ExceptionHandler(RevisionConflictException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleConflict(
      RevisionConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            problems.forStatus(
                409,
                "REVISION_CONFLICT",
                "Revision conflict",
                "The resource was modified by another writer. Fetch the latest"
                    + " revision and retry."));
  }

  @ExceptionHandler(ResourceInUseException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleResourceInUse(
      ResourceInUseException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            problems.forStatus(
                409, "RESOURCE_IN_USE", "Resource is still in use", safeMessage(ex)));
  }

  @ExceptionHandler(ProviderOperationException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleProviderOperation(
      ProviderOperationException ex) {
    return ResponseEntity.status(ex.status())
        .body(
            problems.forStatus(
                ex.status(), ex.code(), "Provider operation failed", safeMessage(ex)));
  }

  @ExceptionHandler(ClientSecretNotAllowedException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleClientSecret(
      ClientSecretNotAllowedException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            problems.forStatus(
                400,
                "CLIENT_SECRET_NOT_ALLOWED",
                "Client secret not allowed",
                "API keys must be stored server-side. Remove the secret field from"
                    + " the request body."));
  }

  /**
   * P3 compat exceptions that MUST be plain text to preserve Node wire compatibility
   * ({@code Invalid limit}, {@code Invalid session share code}, {@code Not found}, etc.). See
   * {@code docs/api/p3-openapi-extensions.yaml} {@code PlainTextError}.
   */
  @ExceptionHandler(PlainTextException.class)
  public ResponseEntity<String> handlePlainText(PlainTextException ex) {
    return ResponseEntity.status(ex.status())
        .contentType(ex.contentType() != null ? ex.contentType() : MediaType.TEXT_PLAIN)
        .body(ex.body());
  }

  /** P3 share-visitor attempting a write while {@code allow-write=false}; ADR-0001. */
  @ExceptionHandler(SharePermissionDeniedException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleSharePermissionDenied(
      SharePermissionDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            problems.forStatus(
                403,
                "SHARE_PERMISSION_DENIED",
                "Share visitor may not mutate this session",
                ex.getMessage() != null && !ex.getMessage().isBlank()
                    ? ex.getMessage()
                    : "Share codes are read-only by default; see ADR-0001."));
  }

  /** P3 revoke with no active share row; ADR-0001. */
  @ExceptionHandler(ShareNotFoundException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleShareNotFound(
      ShareNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            problems.forStatus(
                404,
                "SHARE_NOT_FOUND",
                "No active share for this session",
                "The session has no active share to revoke."));
  }

  /** P3 feedback referencing a missing message; ADR-0003. */
  @ExceptionHandler(FeedbackTargetNotFoundException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleFeedbackTargetNotFound(
      FeedbackTargetNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            problems.forStatus(
                404,
                "FEEDBACK_TARGET_NOT_FOUND",
                "Referenced message does not exist",
                "Feedback references a message that is not present in this session."));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex) {
    var fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(
                fe ->
                    java.util.Map.entry(
                        fe.getField(),
                        (Object)
                            java.util.Map.of(
                                "code",
                                fe.getCode() == null ? "invalid" : fe.getCode(),
                                "message",
                                fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage())))
            .collect(
                java.util.stream.Collectors.toMap(
                    java.util.Map.Entry::getKey,
                    java.util.Map.Entry::getValue,
                    (a, b) -> b,
                    java.util.LinkedHashMap::new));
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            problems.forStatusWithErrors(
                400,
                "INVALID_REQUEST",
                "Validation failed",
                "One or more fields are invalid.",
                fieldErrors));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleIllegal(
      IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problems.forStatus(400, "INVALID_REQUEST", "Invalid request", safeMessage(ex)));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            problems.forStatus(
                500,
                "INTERNAL_ERROR",
                "Internal server error",
                "An unexpected error occurred. Contact support with the request" + " id."));
  }

  private static String safeMessage(Exception ex) {
    String msg = ex.getMessage();
    return msg == null ? "" : msg;
  }
}
