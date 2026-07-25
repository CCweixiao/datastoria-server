package io.datastoria.server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.service.OAuthCredentialService;
import io.datastoria.server.service.OAuthRemoteClient;

import reactor.core.publisher.Mono;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "datastoria.oauth.github.client-id=test-github-client")
@ActiveProfiles("test")
class OAuthCompatibilityApiTest {

  private static final String IDENTITY_HEADER = "x-datastoria-user-email";

  @Autowired WebTestClient web;
  @Autowired TestDbHelper dbHelper;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;
  @Autowired OAuthCredentialService credentials;
  @MockitoBean OAuthRemoteClient remote;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void codexExchangeStoresEncryptedServerSideCredentialAndReturnsOnlyMetadata() throws Exception {
    when(remote.postForm(anyString(), any()))
        .thenReturn(
            Mono.just(
                mapper.readTree(
                    """
                    {
                      "access_token": "codex-access-secret",
                      "refresh_token": "codex-refresh-secret",
                      "token_type": "Bearer",
                      "scope": "openid profile",
                      "expires_in": 3600
                    }
                    """)));

    JsonNode response =
        web.post()
            .uri("/api/ai/codex/auth/token")
            .header(IDENTITY_HEADER, "dev@example.com")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "code": "authorization-code",
                  "code_verifier": "pkce-verifier",
                  "redirect_uri": "http://localhost/callback"
                }
                """)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(JsonNode.class)
            .returnResult()
            .getResponseBody();

    assertThat(response).isNotNull();
    assertThat(response.path("provider").asText()).isEqualTo("codex");
    assertThat(response.path("configured").asBoolean()).isTrue();
    assertThat(response.toString())
        .doesNotContain("codex-access-secret")
        .doesNotContain("codex-refresh-secret");
    assertThat(count("ds_oauth_credential")).isEqualTo(1);
    assertThat(count("ds_secret")).isEqualTo(1);
    byte[] cipherText =
        jdbc.sql("SELECT cipher_text FROM ds_secret").query((rs, row) -> rs.getBytes(1)).single();
    assertThat(new String(cipherText, java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain("codex-access-secret")
        .doesNotContain("codex-refresh-secret");
  }

  @Test
  void expiredCodexAccessTokenIsRefreshedAtTheServerModelBoundary() throws Exception {
    when(remote.postForm(anyString(), any()))
        .thenReturn(
            Mono.just(
                mapper.readTree(
                    """
                    {"access_token":"old-access","refresh_token":"stored-refresh",
                     "expires_in":3600}
                    """)));
    web.post()
        .uri("/api/ai/codex/auth/token")
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {"code":"code","code_verifier":"verifier",
             "redirect_uri":"http://localhost:1455/auth/callback"}
            """)
        .exchange()
        .expectStatus()
        .isOk();

    Map<String, Object> owner =
        jdbc.sql("SELECT tenant_id,user_id FROM ds_oauth_credential WHERE provider_key='codex'")
            .query()
            .singleRow();
    jdbc.sql(
            "UPDATE ds_oauth_credential SET expires_at='2000-01-01T00:00:00'"
                + " WHERE provider_key='codex'")
        .update();
    when(remote.postForm(anyString(), any()))
        .thenReturn(
            Mono.just(
                mapper.readTree(
                    """
                    {"access_token":"new-access","expires_in":3600}
                    """)));

    String token =
        credentials.accessToken(
            "codex",
            new Identity(
                String.valueOf(owner.get("tenant_id")),
                String.valueOf(owner.get("user_id")),
                Set.of("ROLE_USER")));

    assertThat(token).isEqualTo("new-access");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> request = ArgumentCaptor.forClass(Map.class);
    verify(remote, org.mockito.Mockito.times(2)).postForm(anyString(), request.capture());
    assertThat(request.getAllValues().get(1))
        .containsEntry("refresh_token", "stored-refresh")
        .doesNotContainKey("access_token");
  }

  @Test
  void refreshRejectsClientSuppliedToken() {
    web.post()
        .uri("/api/ai/codex/auth/refresh")
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"refresh_token\":\"must-not-enter-browser\"}")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CLIENT_SECRET_NOT_ALLOWED");
  }

  @Test
  void githubDeviceFlowPersistsTokenAndModelsUseStoredCredential() throws Exception {
    when(remote.postJson(eq("https://github.com/login/device/code"), any()))
        .thenReturn(
            Mono.just(
                mapper.readTree(
                    """
                    {
                      "device_code": "device-code",
                      "user_code": "ABCD-EFGH",
                      "verification_uri": "https://github.com/login/device",
                      "expires_in": 900,
                      "interval": 5
                    }
                    """)));
    when(remote.postJson(eq("https://github.com/login/oauth/access_token"), any()))
        .thenReturn(
            Mono.just(
                mapper.readTree(
                    """
                    {
                      "access_token": "github-access-secret",
                      "refresh_token": "github-refresh-secret",
                      "token_type": "bearer",
                      "scope": "read:user"
                    }
                    """)));
    when(remote.getGitHubModels("github-access-secret"))
        .thenReturn(Mono.just(mapper.readTree("{\"data\":[{\"id\":\"gpt-test\"}]}")));

    web.post()
        .uri("/api/ai/github/auth/device/code")
        .header(IDENTITY_HEADER, "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.user_code")
        .isEqualTo("ABCD-EFGH");

    web.post()
        .uri("/api/ai/github/auth/device/token")
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"device_code\":\"device-code\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.provider")
        .isEqualTo("github")
        .jsonPath("$.access_token")
        .doesNotExist();

    web.get()
        .uri("/api/ai/github/models")
        .header(IDENTITY_HEADER, "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.data[0].id")
        .isEqualTo("gpt-test");

    verify(remote).getGitHubModels("github-access-secret");
  }

  @Test
  void githubRefreshRotatesEncryptedSecretUsingStoredRefreshToken() throws Exception {
    when(remote.postJson(eq("https://github.com/login/oauth/access_token"), any()))
        .thenReturn(
            Mono.just(
                mapper.readTree(
                    """
                    {
                      "access_token": "old-access",
                      "refresh_token": "stored-refresh",
                      "token_type": "bearer"
                    }
                    """)));

    web.post()
        .uri("/api/ai/github/auth/device/token")
        .header(IDENTITY_HEADER, "dev@example.com")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"device_code\":\"device-code\"}")
        .exchange()
        .expectStatus()
        .isOk();

    when(remote.postJson(eq("https://github.com/login/oauth/access_token"), any()))
        .thenReturn(
            Mono.just(
                mapper.readTree(
                    """
                    {
                      "access_token": "new-access",
                      "token_type": "bearer"
                    }
                    """)));

    web.post()
        .uri("/api/ai/github/auth/refresh")
        .header(IDENTITY_HEADER, "dev@example.com")
        .exchange()
        .expectStatus()
        .isOk();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> request = ArgumentCaptor.forClass(Map.class);
    verify(remote, org.mockito.Mockito.times(2))
        .postJson(eq("https://github.com/login/oauth/access_token"), request.capture());
    assertThat(request.getAllValues().get(1))
        .containsEntry("refresh_token", "stored-refresh")
        .doesNotContainKey("access_token");
    assertThat(
            jdbc.sql("SELECT COUNT(*) FROM ds_secret WHERE deleted_at IS NULL")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }

  @Test
  void githubModelsRejectsBrowserAuthorizationHeader() {
    web.get()
        .uri("/api/ai/github/models")
        .header(IDENTITY_HEADER, "dev@example.com")
        .header("Authorization", "Bearer browser-secret")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("CLIENT_SECRET_NOT_ALLOWED");
  }

  private long count(String table) {
    return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
  }
}
