package io.github.ccweixiao.datastoria.boot.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;

/** Verifies that apiKey in request bodies is rejected on all relevant endpoints. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ApiKeyRejectionTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void apiKeyRejectedOnAvailableModels() {
    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"apiKey\":\"sk-test123\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CLIENT_SECRET_NOT_ALLOWED");
  }

  @Test
  void emptyBodyAcceptedOnAvailableModels() {
    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void credentialEndpointAcceptsSecretInDesignatedField() {
    String providerId = createProvider();
    web.put()
        .uri("/api/admin/ai/providers/{id}/credential", providerId)
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"secretKind\":\"api_key\",\"value\":\"sk-test1234567890abcdef\",\"expiresAt\":null}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.configured")
        .isEqualTo(true)
        .jsonPath("$.maskedHint")
        .isEqualTo("sk-…def");
  }

  private String createProvider() {
    JsonNode body =
        web.post()
            .uri("/api/admin/ai/providers")
            .header("x-datastoria-user-email", "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                "{\"providerKey\":\"openai\",\"displayName\":\"OpenAI\",\"authType\":\"api_key\","
                    + "\"enabled\":true,\"configJson\":\"{}\"}")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();
    return body.get("id").asText();
  }
}
