package io.github.ccweixiao.datastoria.common.error;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Marker base for P3 compat exceptions that MUST be serialised as a plain-text body to preserve
 * Node.js wire compatibility (see {@code docs/api/p3-openapi-extensions.yaml}, {@code
 * PlainTextError} response).
 *
 * <p>Subclasses carry an {@link HttpStatus} and a fixed body string. The {@link
 * io.github.ccweixiao.datastoria.controller.GlobalExceptionHandler GlobalExceptionHandler} maps
 * every {@code PlainTextException} to {@code text/plain; charset=utf-8} with the configured status
 * and body.
 *
 * <p>This is intentionally separate from the P2 ProblemDetail pathway ({@link NotFoundException} et
 * al.) so the two content types never collide.
 */
public class PlainTextException extends RuntimeException {

  private final HttpStatus status;
  private final String body;
  private final String bodyZh;
  private final ApiErrorCode code;
  private final MediaType contentType;

  public PlainTextException(HttpStatus status, String body) {
    this(status, ApiErrorCode.INVALID_REQUEST, body, body, MediaType.TEXT_PLAIN);
  }

  public PlainTextException(HttpStatus status, String body, MediaType contentType) {
    this(status, ApiErrorCode.INVALID_REQUEST, body, body, contentType);
  }

  private PlainTextException(
      HttpStatus status, ApiErrorCode code, String body, String bodyZh, MediaType contentType) {
    super(body);
    this.status = status;
    this.code = code;
    this.body = body;
    this.bodyZh = bodyZh;
    this.contentType = contentType;
  }

  public HttpStatus status() {
    return status;
  }

  public String body() {
    return body;
  }

  public String body(Locale locale) {
    return ApiErrorCode.isChinese(locale) ? bodyZh : body;
  }

  public ApiErrorCode code() {
    return code;
  }

  public MediaType contentType() {
    return contentType;
  }

  /** HTTP 400 with a caller-visible body. */
  public static PlainTextException badRequest(String body) {
    return new PlainTextException(HttpStatus.BAD_REQUEST, body);
  }

  /** HTTP 400 with a stable error code and a locale-aware body. */
  public static PlainTextException badRequest(ApiErrorCode code) {
    if (code.status() != HttpStatus.BAD_REQUEST.value()) {
      throw new IllegalArgumentException("Error code must use HTTP 400: " + code.name());
    }
    return localized(code, code.message(Locale.ENGLISH), code.message(Locale.SIMPLIFIED_CHINESE));
  }

  /** HTTP 403 with a stable error code and a locale-aware body. */
  public static PlainTextException forbidden(ApiErrorCode code) {
    if (code.status() != HttpStatus.FORBIDDEN.value()) {
      throw new IllegalArgumentException("Error code must use HTTP 403: " + code.name());
    }
    return localized(code, code.message(Locale.ENGLISH), code.message(Locale.SIMPLIFIED_CHINESE));
  }

  /** HTTP 401 with the literal body {@code Authentication required}. */
  public static PlainTextException authenticationRequired() {
    return localized(ApiErrorCode.AUTHENTICATION_REQUIRED, "Authentication required", "需要身份认证");
  }

  /** HTTP 403 with the literal body {@code Invalid session share code}. */
  public static PlainTextException invalidShareCode() {
    return localized(ApiErrorCode.INVALID_SHARE_CODE, "Invalid session share code", "会话共享码无效");
  }

  /** HTTP 404 with the literal body {@code Not found}. */
  public static PlainTextException notFound() {
    return localized(ApiErrorCode.NOT_FOUND, "Not found", "资源不存在");
  }

  /** HTTP 409 with the literal body {@code Session connectionId mismatch}. */
  public static PlainTextException connectionIdMismatch() {
    return localized(
        ApiErrorCode.CONNECTION_ID_MISMATCH,
        "Session connectionId mismatch",
        "会话的 connectionId 不匹配");
  }

  private static PlainTextException localized(ApiErrorCode code, String bodyEn, String bodyZh) {
    return new PlainTextException(
        HttpStatus.valueOf(code.status()), code, bodyEn, bodyZh, MediaType.TEXT_PLAIN);
  }
}
