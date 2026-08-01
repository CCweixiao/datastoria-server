package io.github.ccweixiao.datastoria.controller.compat.p3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Covers the two P3 fixtures that require an explicit "missing header = unauthenticated" semantic
 * which the default {@code dev}/{@code test} profile intentionally disables for developer
 * convenience. The class flips {@code datastoria.identity.allow-anonymous=false} so the test
 * identity filter does not pre-resolve an identity, leaving {@code JwtIdentityWebFilter} to return
 * HTTP 401 with a plain-text body for callers that omit {@code x-datastoria-user-email}.
 *
 * <p>Covers fixtures: {@code A03-list-unauthenticated}, {@code A09-share-unauthenticated}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "datastoria.identity.allow-anonymous=false",
      "datastoria.feedback.store-enabled=true"
    })
class P3UnauthenticatedTest extends AbstractP3ApiTest {

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  @DisplayName("A03-list-unauthenticated: HTTP 401 'Authentication required' (text/plain)")
  void a03ListUnauthenticated() {
    web.get()
        .uri("/api/ai/chat/sessions")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
        .expectBody(String.class)
        .isEqualTo("Authentication required");
  }

  @Test
  @DisplayName("A09-share-unauthenticated: HTTP 401 'Authentication required' (text/plain)")
  void a09ShareUnauthenticated() {
    web.post()
        .uri("/api/ai/sessions/{id}/share", "019523a0f0a64d6c8a3e2b9c1f0d7e20")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.TEXT_PLAIN)
        .expectBody(String.class)
        .isEqualTo("Authentication required");
  }
}
