package io.github.ccweixiao.datastoria.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.MethodNotAllowedException;
import org.springframework.web.util.DisconnectedClientHelper;

import io.github.ccweixiao.datastoria.common.agent.PendingActionConflictException;
import io.github.ccweixiao.datastoria.common.agent.PendingActionExpiredException;
import io.github.ccweixiao.datastoria.common.error.AdminAccessRequiredException;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.BadCredentialsException;
import io.github.ccweixiao.datastoria.common.error.ClientSecretNotAllowedException;
import io.github.ccweixiao.datastoria.common.error.ConflictException;
import io.github.ccweixiao.datastoria.common.error.FeedbackTargetNotFoundException;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.error.ProtectedAdminAccountException;
import io.github.ccweixiao.datastoria.common.error.ProviderOperationException;
import io.github.ccweixiao.datastoria.common.error.ResourceInUseException;
import io.github.ccweixiao.datastoria.common.error.RevisionConflictException;
import io.github.ccweixiao.datastoria.common.error.ShareNotFoundException;
import io.github.ccweixiao.datastoria.common.error.SharePermissionDeniedException;

import reactor.core.publisher.Mono;

/**
 * Translates application exceptions into RFC 9457 {@link org.springframework.http.ProblemDetail}
 * responses. No secret value (cipher text or plaintext) is ever placed in the response body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final DisconnectedClientHelper DISCONNECTED_CLIENTS =
      new DisconnectedClientHelper(GlobalExceptionHandler.class.getName() + ".DisconnectedClient");

  private final ProblemDetailFactory problems;

  public GlobalExceptionHandler(ProblemDetailFactory problems) {
    this.problems = problems;
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleNotFound(
      NotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problems.forError(ApiErrorCode.NOT_FOUND, safeMessage(ex)));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleBadCredentials(
      BadCredentialsException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(problems.forError(ApiErrorCode.AUTHENTICATION_FAILED));
  }

  @ExceptionHandler(AdminAccessRequiredException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleAdminAccessRequired(
      AdminAccessRequiredException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(problems.forError(ApiErrorCode.ADMIN_ACCESS_REQUIRED));
  }

  @ExceptionHandler(ProtectedAdminAccountException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleProtectedAdminAccount(
      ProtectedAdminAccountException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(problems.forError(ApiErrorCode.ADMIN_ACCOUNT_PROTECTED));
  }

  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleConflict(
      ConflictException ex) {
    return ResponseEntity.status(ex.code().status()).body(problems.forError(ex.code()));
  }

  @ExceptionHandler(RevisionConflictException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleConflict(
      RevisionConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(problems.forError(ApiErrorCode.REVISION_CONFLICT));
  }

  @ExceptionHandler(PendingActionConflictException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handlePendingActionConflict(
      PendingActionConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(problems.forError(ApiErrorCode.ACTION_ALREADY_RESOLVED));
  }

  @ExceptionHandler(PendingActionExpiredException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handlePendingActionExpired(
      PendingActionExpiredException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(problems.forError(ApiErrorCode.ACTION_EXPIRED));
  }

  @ExceptionHandler(ResourceInUseException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleResourceInUse(
      ResourceInUseException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(problems.forError(ApiErrorCode.RESOURCE_IN_USE, safeMessage(ex)));
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
        .body(problems.forError(ApiErrorCode.CLIENT_SECRET_NOT_ALLOWED));
  }

  /**
   * P3 compat exceptions that MUST be plain text to preserve Node wire compatibility ({@code
   * Invalid limit}, {@code Invalid session share code}, {@code Not found}, etc.). See {@code
   * docs/api/p3-openapi-extensions.yaml} {@code PlainTextError}.
   */
  @ExceptionHandler(PlainTextException.class)
  public ResponseEntity<String> handlePlainText(PlainTextException ex) {
    java.util.Locale locale = LocaleContextHolder.getLocale();
    return ResponseEntity.status(ex.status())
        .contentType(ex.contentType() != null ? ex.contentType() : MediaType.TEXT_PLAIN)
        .header("X-Error-Code", ex.code().name())
        .header("Content-Language", ApiErrorCode.isChinese(locale) ? "zh-CN" : "en")
        .body(ex.body(locale));
  }

  /** P3 share-visitor attempting a write while {@code allow-write=false}; ADR-0001. */
  @ExceptionHandler(SharePermissionDeniedException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleSharePermissionDenied(
      SharePermissionDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(problems.forError(ApiErrorCode.SHARE_PERMISSION_DENIED));
  }

  /** P3 revoke with no active share row; ADR-0001. */
  @ExceptionHandler(ShareNotFoundException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleShareNotFound(
      ShareNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problems.forError(ApiErrorCode.SHARE_NOT_FOUND));
  }

  /** P3 feedback referencing a missing message; ADR-0003. */
  @ExceptionHandler(FeedbackTargetNotFoundException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleFeedbackTargetNotFound(
      FeedbackTargetNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problems.forError(ApiErrorCode.FEEDBACK_TARGET_NOT_FOUND));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleValidation(
      MethodArgumentNotValidException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problems.forStatusWithErrors(ApiErrorCode.INVALID_REQUEST, fieldErrors(ex)));
  }

  /**
   * WebFlux throws {@link WebExchangeBindException} (not the MVC {@link
   * MethodArgumentNotValidException}) when {@code @Valid} on a {@code @RequestBody} fails; map it
   * to the same 400 field-error shape.
   */
  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleWebFluxBind(
      WebExchangeBindException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problems.forStatusWithErrors(ApiErrorCode.INVALID_REQUEST, fieldErrors(ex)));
  }

  private static java.util.Map<String, Object> fieldErrors(
      org.springframework.validation.BindingResult result) {
    return result.getFieldErrors().stream()
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
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleIllegal(
      IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(problems.forError(ApiErrorCode.INVALID_REQUEST, safeMessage(ex)));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleMissingStaticResource(
      NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(problems.forError(ApiErrorCode.NOT_FOUND));
  }

  @ExceptionHandler(MethodNotAllowedException.class)
  public ResponseEntity<org.springframework.http.ProblemDetail> handleMethodNotAllowed(
      MethodNotAllowedException ex) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .allow(ex.getSupportedMethods().toArray(org.springframework.http.HttpMethod[]::new))
        .body(problems.forError(ApiErrorCode.METHOD_NOT_ALLOWED));
  }

  @ExceptionHandler(Exception.class)
  public Mono<ResponseEntity<org.springframework.http.ProblemDetail>> handleUnexpected(
      Exception ex) {
    // A browser can cancel a streaming query after its 200 response has already been committed
    // (for example when switching tabs or refreshing a dashboard). Treat that transport event as
    // normal cancellation: attempting to write a second error response only produces a misleading
    // ERROR followed by "response already committed".
    if (DISCONNECTED_CLIENTS.checkAndLogClientDisconnectedException(ex)) {
      return Mono.empty();
    }
    log.error("Unexpected error", ex);
    return Mono.just(
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(problems.forError(ApiErrorCode.INTERNAL_ERROR)));
  }

  private static String safeMessage(Exception ex) {
    String msg = ex.getMessage();
    return msg == null ? "" : msg;
  }
}
