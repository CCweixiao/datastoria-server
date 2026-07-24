package io.datastoria.server.api.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.datastoria.server.TestDbHelper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AvailableModelsApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void emptyDbReturnsEmptyArrays() {
    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.systemModels.length()")
        .isEqualTo(0)
        .jsonPath("$.githubModels.length()")
        .isEqualTo(0);
  }

  @Test
  void apiKeyInBodyIsRejectedWith400() {
    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"apiKey\":\"sk-test-1234567890abcdef\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CLIENT_SECRET_NOT_ALLOWED");
  }

  @Test
  void githubTokenIsIgnoredNotRejected() {
    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"github\":{\"token\":\"ghu_abc\"}}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void seededModelsAppearInSystemModels() {
    createProviderAndModel("openai", "gpt-4", "GPT-4");
    createProviderAndModel("anthropic", "claude-3", "Claude 3");

    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.systemModels.length()")
        .isEqualTo(2)
        .jsonPath("$.systemModels[0].provider")
        .isEqualTo("openai")
        .jsonPath("$.systemModels[0].modelId")
        .isEqualTo("gpt-4")
        .jsonPath("$.systemModels[0].free")
        .isEqualTo(false)
        .jsonPath("$.systemModels[1].provider")
        .isEqualTo("anthropic");
  }

  private void createProviderAndModel(String providerKey, String modelKey, String displayName) {
    com.fasterxml.jackson.databind.JsonNode providerResp =
        web.post()
            .uri("/api/admin/ai/providers")
            .header("x-datastoria-user-email", "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                "{\"providerKey\":\""
                    + providerKey
                    + "\",\"displayName\":\""
                    + providerKey
                    + "\",\"authType\":\"api_key\",\"enabled\":true,\"configJson\":\"{}\"}")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(com.fasterxml.jackson.databind.JsonNode.class)
            .returnResult()
            .getResponseBody();
    String providerId = providerResp.get("id").asText();

    web.post()
        .uri("/api/admin/ai/models")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerId\":\""
                + providerId
                + "\",\"modelKey\":\""
                + modelKey
                + "\",\"displayName\":\""
                + displayName
                + "\",\"source\":\"custom\",\"enabled\":true,\"isFree\":false}")
        .exchange()
        .expectStatus()
        .is2xxSuccessful();
  }
}
