package io.github.ccweixiao.datastoria.boot.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;

/**
 * Verifies that plaintext secrets never appear in API responses. The credential write endpoint
 * stores the secret encrypted and returns only a masked hint; all list/get endpoints omit the
 * secret entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecretRedactionTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  private static final String SECRET = "sk-supersecret-1234567890abcdef";

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void credentialResponseOmitsPlaintext() {
    String providerId = createProvider();
    JsonNode body = putCredential(providerId, SECRET);
    assertThat(body.toString()).doesNotContain(SECRET);
    assertThat(body.get("configured").asBoolean()).isTrue();
    assertThat(body.get("maskedHint").asText()).startsWith("sk-");
  }

  @Test
  void providerListOmitsPlaintext() {
    String providerId = createProvider();
    putCredential(providerId, SECRET);

    JsonNode list =
        web.get()
            .uri("/api/admin/ai/providers")
            .header("x-datastoria-user-email", "dev@example.com")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();
    assertThat(list.toString()).doesNotContain(SECRET);
  }

  @Test
  void providerGetOmitsPlaintext() {
    String providerId = createProvider();
    putCredential(providerId, SECRET);

    JsonNode detail =
        web.get()
            .uri("/api/admin/ai/providers/{id}", providerId)
            .header("x-datastoria-user-email", "dev@example.com")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();
    assertThat(detail.toString()).doesNotContain(SECRET);
    assertThat(detail.get("credentialConfigured").asBoolean()).isTrue();
    assertThat(detail.has("cipherText")).isFalse();
    assertThat(detail.has("nonce")).isFalse();
    assertThat(detail.has("value")).isFalse();
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

  private JsonNode putCredential(String providerId, String secret) {
    return web.put()
        .uri("/api/admin/ai/providers/{id}/credential", providerId)
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"secretKind\":\"api_key\",\"value\":\"" + secret + "\",\"expiresAt\":null}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(JsonNode.class)
        .returnResult()
        .getResponseBody();
  }
}
