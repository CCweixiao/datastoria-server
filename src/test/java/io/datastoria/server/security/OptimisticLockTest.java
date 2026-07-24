package io.datastoria.server.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.TestDbHelper;

/** Verifies that stale If-Match headers cause 409 REVISION_CONFLICT across resources. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OptimisticLockTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void staleIfMatchOnProviderReturns409() {
    String id = createProvider();
    web.put()
        .uri("/api/admin/ai/providers/{id}", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "99")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerKey\":\"openai\",\"displayName\":\"Updated\",\"authType\":\"api_key\","
                + "\"enabled\":true,\"configJson\":\"{}\"}")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void staleIfMatchOnAgentPublishReturns409() {
    String agentId = createAgent();
    String revId = createRevision(agentId);
    web.post()
        .uri("/api/admin/ai/agents/{id}/revisions/{revisionId}:publish", agentId, revId)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "99")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void staleIfMatchOnUserPreferenceReturns409() {
    web.put()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"configKey\":\"theme\",\"valueJson\":\"\\\"dark\\\"\"}")
        .exchange()
        .expectStatus()
        .isOk();

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

  private String createAgent() {
    JsonNode body =
        web.post()
            .uri("/api/admin/ai/agents")
            .header("x-datastoria-user-email", "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"agentKey\":\"main\",\"name\":\"Main\"}")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();
    return body.get("id").asText();
  }

  private String createRevision(String agentId) {
    JsonNode body =
        web.post()
            .uri("/api/admin/ai/agents/{id}/revisions", agentId)
            .header("x-datastoria-user-email", "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                "{\"modelId\":null,\"systemPrompt\":\"test\",\"runtimeConfigJson\":null,"
                    + "\"toolPolicyJson\":null,\"skillPolicyJson\":null}")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();
    return body.get("id").asText();
  }
}
