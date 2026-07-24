package io.datastoria.server.api.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.TestDbHelper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ModelAdminApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void createAndListModel() {
    String providerId = createProvider();
    String modelId = createModel(providerId, "gpt-4", "GPT-4");

    web.get()
        .uri("/api/admin/ai/models")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].id")
        .isEqualTo(modelId)
        .jsonPath("$[0].modelKey")
        .isEqualTo("gpt-4")
        .jsonPath("$[0].source")
        .isEqualTo("system");
  }

  @Test
  void getModelDetailReturnsEtag() {
    String providerId = createProvider();
    String modelId = createModel(providerId, "gpt-4", "GPT-4");
    web.get()
        .uri("/api/admin/ai/models/{id}", modelId)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("ETag", "\"0\"")
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(modelId);
  }

  @Test
  void updateModelWithIfMatch() {
    String providerId = createProvider();
    String modelId = createModel(providerId, "gpt-4", "GPT-4");

    web.put()
        .uri("/api/admin/ai/models/{id}", modelId)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "0")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
                {"displayName":"GPT-4 Turbo","description":"Updated","source":"system",\
"enabled":false,"isFree":false,"capabilitiesJson":"{}","generationDefaultsJson":"{}"}
                """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revision")
        .isEqualTo(1)
        .jsonPath("$.displayName")
        .isEqualTo("GPT-4 Turbo")
        .jsonPath("$.enabled")
        .isEqualTo(false);
  }

  @Test
  void deleteModelReturns204() {
    String providerId = createProvider();
    String modelId = createModel(providerId, "gpt-4", "GPT-4");

    web.delete()
        .uri("/api/admin/ai/models/{id}", modelId)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "0")
        .exchange()
        .expectStatus()
        .isNoContent();

    web.get()
        .uri("/api/admin/ai/models")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectBody()
        .jsonPath("$")
        .isArray()
        .jsonPath("$.length()")
        .isEqualTo(0);
  }

  private String createProvider() {
    return web.post()
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
        .getResponseBody()
        .get("id")
        .asText();
  }

  private String createModel(String providerId, String key, String name) {
    return web.post()
        .uri("/api/admin/ai/models")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerId\":\""
                + providerId
                + "\",\"modelKey\":\""
                + key
                + "\",\"displayName\":\""
                + name
                + "\",\"source\":\"system\",\"enabled\":true,\"isFree\":false,"
                + "\"capabilitiesJson\":\"{}\",\"generationDefaultsJson\":\"{}\"}")
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(JsonNode.class)
        .returnResult()
        .getResponseBody()
        .get("id")
        .asText();
  }
}
