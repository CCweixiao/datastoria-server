package io.github.ccweixiao.datastoria.controller.admin;

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

import io.github.ccweixiao.datastoria.boot.TestDbHelper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AgentAdminApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;
  @Autowired ObjectMapper objectMapper;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void createAgentReturnsDraftWithZeroRevision() {
    String id = createAgent("main", "Main Agent");

    web.get()
        .uri("/api/admin/ai/agents/{id}", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo(id)
        .jsonPath("$.agentKey")
        .isEqualTo("main")
        .jsonPath("$.name")
        .isEqualTo("Main Agent")
        .jsonPath("$.status")
        .isEqualTo("draft")
        .jsonPath("$.revision")
        .isEqualTo(0)
        .jsonPath("$.revisions")
        .isArray()
        .jsonPath("$.revisions.length()")
        .isEqualTo(0);
  }

  @Test
  void createRevisionPublishesAndBumpsDefinitionRevision() {
    String agentId = createAgent("main", "Main Agent");
    String revId = createRevision(agentId, "You are helpful");

    web.post()
        .uri("/api/admin/ai/agents/{id}/revisions/{revisionId}:publish", agentId, revId)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "0")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("published")
        .jsonPath("$.publishedRevisionId")
        .isEqualTo(revId)
        .jsonPath("$.revision")
        .isEqualTo(1);

    // Revision is now included in the agent's revisions list
    web.get()
        .uri("/api/admin/ai/agents/{id}", agentId)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.revisions[0].id")
        .isEqualTo(revId)
        .jsonPath("$.revisions[0].version")
        .isEqualTo(1)
        .jsonPath("$.revisions[0].systemPrompt")
        .isEqualTo("You are helpful");
  }

  @Test
  void disableAgentSetsStatusToDisabled() {
    String agentId = createAgent("main", "Main Agent");

    web.post()
        .uri("/api/admin/ai/agents/{id}:disable", agentId)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "0")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("disabled");
  }

  @Test
  void publishWithStaleIfMatchReturns409() {
    String agentId = createAgent("main", "Main Agent");
    String revId = createRevision(agentId, "v1");

    web.post()
        .uri("/api/admin/ai/agents/{id}/revisions/{revisionId}:publish", agentId, revId)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "99")
        .exchange()
        .expectStatus()
        .isEqualTo(409);
  }

  @Test
  void publishNonExistentAgentReturns404() {
    web.post()
        .uri("/api/admin/ai/agents/non-existent/revisions/rev:publish")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("NOT_FOUND");
  }

  @Test
  void listAgentsReturnsAllAgents() {
    createAgent("main", "Main Agent");
    createAgent("helper", "Helper Agent");

    web.get()
        .uri("/api/admin/ai/agents")
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(2);
  }

  @Test
  void responseCarriesEtagHeader() {
    String id = createAgent("main", "Main Agent");
    String etag =
        web.get()
            .uri("/api/admin/ai/agents/{id}", id)
            .header("x-datastoria-user-email", "dev@example.com")
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .valueMatches("ETag", "\"?\\d+\"?")
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseHeaders()
            .getETag();
    assertThat(etag).contains("0");
  }

  @Test
  void updateAndSoftDeleteAgent() {
    String id = createAgent("main", "Main Agent");
    web.put()
        .uri("/api/admin/ai/agents/{id}", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "\"0\"")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"name\":\"Renamed\",\"description\":\"Updated\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.name")
        .isEqualTo("Renamed")
        .jsonPath("$.revision")
        .isEqualTo(1);

    web.delete()
        .uri("/api/admin/ai/agents/{id}", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .header("If-Match", "\"1\"")
        .exchange()
        .expectStatus()
        .isNoContent();
    web.get()
        .uri("/api/admin/ai/agents/{id}", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  private String createAgent(String key, String name) {
    return web.post()
        .uri("/api/admin/ai/agents")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"agentKey\":\"" + key + "\",\"name\":\"" + name + "\",\"description\":null}")
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody(JsonNode.class)
        .returnResult()
        .getResponseBody()
        .get("id")
        .asText();
  }

  private String createRevision(String agentId, String prompt) {
    return web.post()
        .uri("/api/admin/ai/agents/{id}/revisions", agentId)
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"modelId\":null,\"systemPrompt\":\""
                + prompt
                + "\",\"runtimeConfigJson\":null,\"toolPolicyJson\":null,\"skillPolicyJson\":null}")
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
