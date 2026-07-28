package io.github.ccweixiao.datastoria.controller.compat.p3;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Covers the {@code A10-feedback-accepted-not-stored} fixture: when the feedback store is disabled
 * ({@code datastoria.feedback.store-enabled=false}), the service returns HTTP 202 with the body
 * {@code {recorded:false}} without touching the database.
 *
 * <p>In production this condition corresponds to a deployment with no remote store wired (per the
 * OpenAPI {@code x-datastoria-node-baseline} for A10). In tests we model it via the same flag so
 * the wire shape is exercised end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "datastoria.feedback.store-enabled=false",
      "datastoria.identity.allow-anonymous=true"
    })
class P3FeedbackAcceptedTest extends AbstractP3ApiTest {

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  @DisplayName("A10-feedback-accepted-not-stored: HTTP 202 {recorded:false}")
  void a10FeedbackAcceptedNotStored() {
    web.post()
        .uri("/api/ai/chat/feedback/auto-explain")
        .header("x-datastoria-user-email", OWNER_EMAIL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "source", "auto_explain_error",
                "sessionId", "019523a0f0a64d6c8a3e2b9c1f0d7e20",
                "messageId", "msg_019523a101",
                "solved", false,
                "reasonCode", "too_vague",
                "payload", Map.of("queryId", "q_43")))
        .exchange()
        .expectStatus()
        .isAccepted()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.recorded")
        .isEqualTo(false);
  }
}
