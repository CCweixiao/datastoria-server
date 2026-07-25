package io.datastoria.server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;

class DevelopmentCorsConfigTest {

  @Test
  void allowsEveryMethodAndHeaderUsedByTheSeparateFrontend() {
    var source =
        new DevelopmentCorsConfig()
            .corsConfigurationSource(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.options("/api/ai/agent")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.PATCH.name())
                .build());

    CorsConfiguration configuration = source.getCorsConfiguration(exchange);

    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowedOrigins())
        .containsExactly("http://localhost:3000", "http://127.0.0.1:3000");
    assertThat(configuration.getAllowedMethods())
        .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    assertThat(configuration.getAllowedHeaders())
        .contains(
            "Content-Type",
            "Idempotency-Key",
            "If-Match",
            "Last-Event-ID",
            "X-Session-Share-Code",
            "x-datastoria-user-email");
    assertThat(configuration.getExposedHeaders())
        .contains(
            "ETag",
            "Deprecation",
            "Link",
            "Location",
            "X-ClickHouse-Exception-Code",
            "X-ClickHouse-Query-Id",
            "X-ClickHouse-Server-Display-Name",
            "X-ClickHouse-Summary");
    assertThat(configuration.getAllowCredentials()).isTrue();
  }
}
