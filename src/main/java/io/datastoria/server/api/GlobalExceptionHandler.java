package io.datastoria.server.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.datastoria.server.api.error.ClientSecretNotAllowedException;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.ProviderOperationException;
import io.datastoria.server.api.error.ResourceInUseException;
import io.datastoria.server.api.error.RevisionConflictException;

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
