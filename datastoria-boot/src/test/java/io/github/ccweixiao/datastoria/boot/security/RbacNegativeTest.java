package io.github.ccweixiao.datastoria.boot.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;

/**
 * Verifies that a user without ROLE_ADMIN is forbidden from admin endpoints while still being
 * allowed on user and compat endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class RbacNegativeTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;

  private static final String REGULAR_USER = "user@example.com";
  private static final String ADMIN_USER = "dev@example.com";

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void regularUserGets403OnAdminProviders() {
    web.get()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", REGULAR_USER)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void regularUserGets403OnAdminModels() {
    web.post()
        .uri("/api/admin/ai/models")
        .header("x-datastoria-user-email", REGULAR_USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerId\":\"x\",\"modelKey\":\"y\",\"displayName\":\"Y\","
                + "\"source\":\"custom\",\"enabled\":true}")
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void regularUserGets403OnAdminAgents() {
    web.get()
        .uri("/api/admin/ai/agents")
        .header("x-datastoria-user-email", REGULAR_USER)
        .exchange()
        .expectStatus()
        .isForbidden();
  }

  @Test
  void regularUserCanAccessUserPreferences() {
    web.get()
        .uri("/api/me/ai/preferences")
        .header("x-datastoria-user-email", REGULAR_USER)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void regularUserCanAccessAvailableModels() {
    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", REGULAR_USER)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void adminUserCanAccessAdminEndpoints() {
    web.get()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", ADMIN_USER)
        .exchange()
        .expectStatus()
        .isOk();
  }
}
