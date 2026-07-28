package io.github.ccweixiao.datastoria.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Builds RFC 9457 {@link ProblemDetail} instances with a stable {@code code}, {@code requestId} and
 * {@code type} URI convention.
 */
@Component
public class ProblemDetailFactory {

  private static final String TYPE_BASE = "https://datastoria.io/problems/";

  public ProblemDetail forStatus(int status, String code, String title, String detail) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    problem.setTitle(title);
    problem.setType(java.net.URI.create(TYPE_BASE + kebab(code)));
    problem.setProperty("code", code);
    problem.setProperty("requestId", UUID.randomUUID().toString());
    return problem;
  }

  public ProblemDetail forStatusWithErrors(
      int status, String code, String title, String detail, Map<String, Object> errors) {
    ProblemDetail problem = forStatus(status, code, title, detail);
    problem.setProperty("errors", errors);
    return problem;
  }

  private static String kebab(String code) {
    return code.toLowerCase().replace('_', '-');
  }
}
