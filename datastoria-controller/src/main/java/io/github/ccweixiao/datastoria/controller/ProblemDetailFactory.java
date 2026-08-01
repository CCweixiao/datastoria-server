package io.github.ccweixiao.datastoria.controller;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;

/**
 * Builds RFC 9457 {@link ProblemDetail} instances with a stable {@code code}, {@code requestId} and
 * {@code type} URI convention.
 */
@Component
public class ProblemDetailFactory {

  private static final String TYPE_BASE = "https://datastoria.io/problems/";

  public ProblemDetail forError(ApiErrorCode error) {
    Locale locale = LocaleContextHolder.getLocale();
    return forStatus(
        error.status(), error.name(), error.title(locale), error.message(locale), locale);
  }

  public ProblemDetail forError(ApiErrorCode error, String safeDetail) {
    Locale locale = LocaleContextHolder.getLocale();
    String detail = safeDetail == null || safeDetail.isBlank() ? error.message(locale) : safeDetail;
    return forStatus(error.status(), error.name(), error.title(locale), detail, locale);
  }

  public ProblemDetail forStatus(int status, String code, String title, String detail) {
    return forStatus(status, code, title, detail, LocaleContextHolder.getLocale());
  }

  private ProblemDetail forStatus(
      int status, String code, String title, String detail, Locale locale) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    problem.setTitle(title);
    problem.setType(java.net.URI.create(TYPE_BASE + kebab(code)));
    problem.setProperty("code", code);
    problem.setProperty("message", detail);
    problem.setProperty("locale", ApiErrorCode.isChinese(locale) ? "zh-CN" : "en");
    problem.setProperty("requestId", UUID.randomUUID().toString());
    return problem;
  }

  public ProblemDetail forStatusWithErrors(
      int status, String code, String title, String detail, Map<String, Object> errors) {
    ProblemDetail problem = forStatus(status, code, title, detail);
    problem.setProperty("errors", errors);
    return problem;
  }

  public ProblemDetail forStatusWithErrors(ApiErrorCode error, Map<String, Object> errors) {
    ProblemDetail problem = forError(error);
    problem.setProperty("errors", errors);
    return problem;
  }

  private static String kebab(String code) {
    return code.toLowerCase().replace('_', '-');
  }
}
