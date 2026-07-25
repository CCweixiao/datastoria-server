package io.datastoria.server.api.compat;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

/**
 * Scoped advice for P3 compat controllers ({@code io.datastoria.server.api.compat}). Translates
 * body-decode failures into the Node-compat plain-text {@code Invalid JSON in request body}
 * response documented in {@code docs/api/p3-openapi-extensions.yaml} PlainTextError.
 *
 * <p>The global {@link io.datastoria.server.api.GlobalExceptionHandler GlobalExceptionHandler}
 * would otherwise emit a {@code 500 INTERNAL_ERROR} ProblemDetail because the WebFlux codec throws
 * before the controller is reached. The base-package scan on this advice keeps the translation
 * scoped to P3 — P2 admin routes keep their default ProblemDetail behaviour.
 */
@RestControllerAdvice(basePackages = "io.datastoria.server.api.compat")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CompatExceptionHandler {

  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<String> handleServerWebInput(ServerWebInputException ex) {
    // Any WebFlux input failure on a compat route is reported as the Node-compat plain-text
    // "Invalid JSON in request body" message. The status is 400 unless the underlying cause
    // explicitly set something more serious (rare in practice for body-decode failures).
    HttpStatus status =
        HttpStatus.resolve(ex.getStatusCode().value()) != null
            ? HttpStatus.resolve(ex.getStatusCode().value())
            : HttpStatus.BAD_REQUEST;
    if (status.is5xxServerError()) {
      status = HttpStatus.BAD_REQUEST;
    }
    return ResponseEntity.status(status)
        .contentType(MediaType.TEXT_PLAIN)
        .body("Invalid JSON in request body");
  }
}
