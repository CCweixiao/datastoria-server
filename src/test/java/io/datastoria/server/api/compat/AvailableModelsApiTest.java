package io.datastoria.server.api.compat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.service.OAuthRemoteClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "datastoria.oauth.github.client-id=test-client")
@ActiveProfiles("test")
class AvailableModelsApiTest {

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
  @MockitoBean OAuthRemoteClient oauthRemote;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void emptyDbIsProvisionedWithBackendManagedBuiltins() {
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
        .isEqualTo(4)
        .jsonPath("$.systemModels[*].modelId")
        .value(org.hamcrest.Matchers.hasItem("gpt-5.4"))
        .jsonPath("$.systemModels[*].supportsReasoning")
        .value(org.hamcrest.Matchers.hasItem(true))
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
  void githubTokenIsRejected() {
    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"github\":{\"token\":\"ghu_abc\"}}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CLIENT_SECRET_NOT_ALLOWED");
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
        .isEqualTo(6)
        .jsonPath("$.systemModels[*].modelId")
        .value(org.hamcrest.Matchers.hasItems("gpt-4", "claude-3"));
  }

  @Test
  void storedGitHubOAuthCredentialPopulatesGitHubModels() throws Exception {
    org.mockito.Mockito.when(
            oauthRemote.postJson(
                org.mockito.ArgumentMatchers.eq("https://github.com/login/oauth/access_token"),
                org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            reactor.core.publisher.Mono.just(
                mapper.readTree(
                    """
                    {"access_token":"stored-token","refresh_token":"refresh-token"}
                    """)));
    org.mockito.Mockito.when(oauthRemote.getGitHubModels("stored-token"))
        .thenReturn(
            reactor.core.publisher.Mono.just(
                mapper.readTree(
                    """
                    {"data":[{"id":"copilot-model","name":"Copilot Model",
                    "vendor":"GitHub","model_picker_enabled":true,
                    "supported_endpoints":["chat"]}]}
                    """)));

    web.post()
        .uri("/api/ai/github/auth/device/token")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"device_code\":\"device-code\"}")
        .exchange()
        .expectStatus()
        .isOk();

    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.systemModels[*].modelId")
        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("copilot-model")))
        .jsonPath("$.githubModels[0].provider")
        .isEqualTo("GitHub Copilot")
        .jsonPath("$.githubModels[0].modelId")
        .isEqualTo("copilot-model")
        .jsonPath("$.githubModels[0].configId")
        .isNotEmpty();

    web.post()
        .uri("/api/ai/models/available")
        .header("x-datastoria-user-email", "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.systemModels[*].modelId")
        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("copilot-model")))
        .jsonPath("$.githubModels.length()")
        .isEqualTo(1);
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
