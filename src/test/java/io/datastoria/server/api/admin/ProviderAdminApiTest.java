package io.datastoria.server.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.TestDbHelper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProviderAdminApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void createAndListProvider() {
    String id = createProvider("openai", "OpenAI");

    web.get()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].id")
        .isEqualTo(id)
        .jsonPath("$[0].providerKey")
        .isEqualTo("openai")
        .jsonPath("$[0].credentialConfigured")
        .isEqualTo(false);
  }

  @Test
  void putCredentialReturnsMaskedHintNeverPlaintext() {
    String providerId = createProvider("openai", "OpenAI");

    JsonNode body = putCredential(providerId, "sk-test-1234567890abcdef");

    assertThat(body.get("configured").asBoolean()).isTrue();
    assertThat(body.get("maskedHint").asText()).isEqualTo("sk-…def");
    assertThat(body.toString()).doesNotContain("sk-test-1234567890abcdef");

    // Provider list now shows credential configured
    web.get()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].credentialConfigured")
        .isEqualTo(true)
        .jsonPath("$[0].maskedHint")
        .isEqualTo("sk-…def");
  }

  @Test
  void deleteCredentialClearsStatus() {
    String providerId = createProvider("openai", "OpenAI");
    putCredential(providerId, "sk-test-1234567890abcdef");

    web.delete()
        .uri("/api/admin/ai/providers/{id}/credential", providerId)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isNoContent();

    web.get()
        .uri("/api/admin/ai/providers/{id}", providerId)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.credentialConfigured")
        .isEqualTo(false);
  }

  @Test
  void updateWithIfMatchIncrementsRevision() {
    String id = createProvider("openai", "OpenAI");

    web.put()
        .uri("/api/admin/ai/providers/{id}", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "\"0\"")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
                {"displayName":"OpenAI Pro","baseUrl":null,"authType":"api_key",\
"enabled":true,"configJson":"{}"}
                """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revision")
        .isEqualTo(1)
        .jsonPath("$.displayName")
        .isEqualTo("OpenAI Pro");
  }

  @Test
  void updateWithStaleIfMatchReturns409() {
    String id = createProvider("openai", "OpenAI");
    web.put()
        .uri("/api/admin/ai/providers/{id}", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "99")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
                {"displayName":"Stale","baseUrl":null,"authType":"api_key",\
"enabled":true,"configJson":"{}"}
                """)
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void getNonExistentProviderReturns404ProblemDetail() {
    web.get()
        .uri("/api/admin/ai/providers/non-existent")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void deleteProviderAndRejectDeleteWhileModelUsesIt() {
    String unused = createProvider("unused", "Unused");
    web.delete()
        .uri("/api/admin/ai/providers/{id}", unused)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "\"0\"")
        .exchange()
        .expectStatus()
        .isNoContent();

    String used = createProvider("used", "Used");
    web.post()
        .uri("/api/admin/ai/models")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerId\":\""
                + used
                + "\",\"modelKey\":\"m\",\"displayName\":\"M\","
                + "\"source\":\"custom\",\"enabled\":true}")
        .exchange()
        .expectStatus()
        .isOk();
    web.delete()
        .uri("/api/admin/ai/providers/{id}", used)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("RESOURCE_IN_USE");
  }

  private String createProvider(String key, String name) {
    return web.post()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerKey\":\""
                + key
                + "\",\"displayName\":\""
                + name
                + "\",\"authType\":\"api_key\",\"enabled\":true,\"configJson\":\"{}\"}")
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(JsonNode.class)
        .returnResult()
        .getResponseBody()
        .get("id")
        .asText();
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
