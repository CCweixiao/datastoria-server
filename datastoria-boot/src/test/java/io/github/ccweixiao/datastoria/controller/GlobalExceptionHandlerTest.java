package io.github.ccweixiao.datastoria.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(new ProblemDetailFactory());

  @Test
  void ignoresClientDisconnectWithoutAttemptingAnotherResponse() {
    assertThat(handler.handleUnexpected(new AbortedException()).blockOptional()).isEmpty();
  }

  @Test
  void preservesInternalServerErrorForTrulyUnexpectedFailures() {
    var response = handler.handleUnexpected(new IllegalStateException("boom")).block();

    assertThat(response).isNotNull();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
  }

  /** Spring identifies client disconnects by known exception type names across server runtimes. */
  private static final class AbortedException extends RuntimeException {}
}
