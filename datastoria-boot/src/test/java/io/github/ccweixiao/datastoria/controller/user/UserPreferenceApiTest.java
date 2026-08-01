package io.github.ccweixiao.datastoria.controller.user;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class UserPreferenceApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void putPreferenceThenGetReflectsValue() {
    web.put()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"configKey\":\"theme\",\"valueJson\":\"\\\"dark\\\"\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.entries.theme")
        .isEqualTo("\"dark\"");

    web.get()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.entries.theme")
        .isEqualTo("\"dark\"")
        .jsonPath("$.revision")
        .isEqualTo(0);
  }

  @Test
  void putPreferenceWithStaleIfMatchReturns409() {
    // First put to establish revision 0
    web.put()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"configKey\":\"theme\",\"valueJson\":\"\\\"dark\\\"\"}")
        .exchange()
        .expectStatus()
        .isOk();

    // Stale If-Match should fail
    web.put()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "99")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"configKey\":\"theme\",\"valueJson\":\"\\\"light\\\"\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void putThenUpdateBumpsRevisionAndEtag() {
    String etag1 =
        web.put()
            .uri("/api/me/ai/preferences")
            .header("x-datastoria-user-email", "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"configKey\":\"lang\",\"valueJson\":\"\\\"en\\\"\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseHeaders()
            .getETag();

    String etag2 =
        web.put()
            .uri("/api/me/ai/preferences")
            .header("x-datastoria-user-email", "dev@example.com")
            .header("If-Match", "0")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"configKey\":\"lang\",\"valueJson\":\"\\\"fr\\\"\"}")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.entries.lang")
            .isEqualTo("\"fr\"")
            .jsonPath("$.revision")
            .isEqualTo(1)
            .returnResult()
            .getResponseHeaders()
            .getETag();

    assertThat(etag1).isNotEqualTo(etag2);
  }

  @Test
  void modelPreferencePutAndGet() {
    String modelId = createModel();

    web.put()
        .uri("/api/me/ai/model-preference")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"modelConfigId\":\"" + modelId + "\",\"preferenceJson\":null}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.selectedModelId")
        .isEqualTo(modelId);

    web.get()
        .uri("/api/me/ai/model-preference")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.selectedModelId")
        .isEqualTo(modelId);
  }

  @Test
  void getPreferencesMaterializesSystemDefaults() {
    web.get()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.entries.length()")
        .isEqualTo(3)
        .jsonPath("$.entries['settings.ai.agent']")
        .exists()
        .jsonPath("$.entries['settings.query-context']")
        .isEqualTo("{\"max_execution_time\": 60}")
        .jsonPath("$.entries['settings.ui']")
        .isEqualTo("{\"theme\": \"dark\"}")
        .jsonPath("$.revision")
        .isEqualTo(0);
  }

  private String createModel() {
    String providerId =
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
            .getResponseBody()
            .get("id")
            .asText();

    return web.post()
        .uri("/api/admin/ai/models")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerId\":\""
                + providerId
                + "\",\"modelKey\":\"gpt-4\",\"displayName\":\"GPT-4\","
                + "\"source\":\"custom\",\"enabled\":true,\"isFree\":false}")
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
