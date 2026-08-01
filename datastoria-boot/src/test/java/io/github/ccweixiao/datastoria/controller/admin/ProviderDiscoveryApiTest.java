package io.github.ccweixiao.datastoria.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;
import io.github.ccweixiao.datastoria.common.dto.DiscoveredModelResponse;
import io.github.ccweixiao.datastoria.service.ProviderRemoteClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ProviderDiscoveryApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;
  @MockitoBean ProviderRemoteClient remoteClient;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void testAndDiscoverUseServerSideCredential() {
    String id = createProvider();
    putCredential(id);
    when(remoteClient.discoverModels(any(), eq("sk-server-only")))
        .thenReturn(List.of(new DiscoveredModelResponse("gpt-test", "gpt-test", "openai")));

    web.post()
        .uri("/api/admin/ai/providers/{id}:test", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.success")
        .isEqualTo(true);

    web.post()
        .uri("/api/admin/ai/providers/{id}/models:discover", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].modelKey")
        .isEqualTo("gpt-test")
        .jsonPath("$")
        .value(
            value ->
                org.assertj.core.api.Assertions.assertThat(value.toString())
                    .doesNotContain("sk-server-only"));
  }

  private String createProvider() {
    return web.post()
        .uri("/api/admin/ai/providers")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"providerKey\":\"openai\",\"displayName\":\"OpenAI\","
                + "\"authType\":\"api_key\",\"enabled\":true}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(JsonNode.class)
        .returnResult()
        .getResponseBody()
        .get("id")
        .asText();
  }

  private void putCredential(String id) {
    web.put()
        .uri("/api/admin/ai/providers/{id}/credential", id)
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"secretKind\":\"api_key\",\"value\":\"sk-server-only\"}")
        .exchange()
        .expectStatus()
        .isOk();
  }
}
