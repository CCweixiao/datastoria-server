package io.datastoria.server.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.TestDbHelper;

/**
 * Verifies that data created by tenant A is invisible to tenant B. Every query includes tenant_id
 * from the server-resolved Identity, never from the request body.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CrossTenantIsolationTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  private static final String TENANT_A = "dev@example.com";
  private static final String TENANT_B = "tenant-b@example.com";

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void providerCreatedInTenantAIsInvisibleToTenantB() {
    createProvider(TENANT_A, "openai");

    // Tenant B lists providers — should be empty
    web.get()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", TENANT_B)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(0);

    // Tenant A sees their provider
    web.get()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", TENANT_A)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.length()")
        .isEqualTo(1);
  }

  @Test
  void agentCreatedInTenantAIsInvisibleToTenantB() {
    String agentId = createAgent(TENANT_A, "main");

    // Tenant B cannot fetch it by id
    web.get()
        .uri("/api/admin/ai/agents/{id}", agentId)
        .header("x-datastoria-user-email", TENANT_B)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void userPreferencesAreTenantScoped() {
    // Tenant A user writes a preference
    web.put()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", TENANT_A)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"configKey\":\"theme\",\"valueJson\":\"\\\"dark\\\"\"}")
        .exchange()
        .expectStatus()
        .isOk();

    // Tenant B user has a different effective config (no theme)
    web.get()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", TENANT_B)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.entries.theme")
        .doesNotExist();
  }

  private void createProvider(String email, String key) {
    web.post()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", email)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerKey\":\""
                + key
                + "\",\"displayName\":\""
                + key
                + "\",\"authType\":\"api_key\",\"enabled\":true,\"configJson\":\"{}\"}")
        .exchange()
        .expectStatus()
        .is2xxSuccessful();
  }

  private String createAgent(String email, String key) {
    JsonNode body =
        web.post()
            .uri("/api/admin/ai/agents")
            .header("x-datastoria-user-email", email)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"agentKey\":\"" + key + "\",\"name\":\"" + key + "\"}")
            .exchange()
            .expectStatus()
            .is2xxSuccessful()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).isNotNull();
    return body.get("id").asText();
  }
}
