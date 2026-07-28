package io.github.ccweixiao.datastoria.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.dto.OAuthCredentialResponse;
import io.github.ccweixiao.datastoria.common.error.ClientSecretNotAllowedException;
import io.github.ccweixiao.datastoria.common.error.ProviderOperationException;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.OAuthCredentialService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ai")
public class OAuthCompatibilityController {

  private static final String CODEX_TOKEN_URL = "https://auth.openai.com/oauth/token";

  private final OAuthCredentialService service;
  private final String codexClientId;
  private final String githubClientId;

  public OAuthCompatibilityController(
      OAuthCredentialService service,
      @Value("${datastoria.oauth.codex.client-id:app_EMoamEEZ73f0CkXaXp7hrann}")
          String codexClientId,
      @Value("${datastoria.oauth.github.client-id:}") String githubClientId) {
    this.service = service;
    this.codexClientId = codexClientId;
    this.githubClientId = githubClientId;
  }

  @PostMapping("/codex/auth/token")
  public Mono<OAuthCredentialResponse> codexToken(@RequestBody JsonNode body) {
    return IdentityContext.current()
        .flatMap(
            identity ->
                service.exchangeCodex(
                    required(body, "code"),
                    required(body, "code_verifier"),
                    required(body, "redirect_uri"),
                    codexClientId,
                    CODEX_TOKEN_URL,
                    identity));
  }

  @PostMapping("/codex/auth/refresh")
  public Mono<OAuthCredentialResponse> codexRefresh(@RequestBody(required = false) JsonNode body) {
    rejectClientToken(body);
    return IdentityContext.current()
        .flatMap(identity -> service.refreshCodex(codexClientId, CODEX_TOKEN_URL, identity));
  }

  @PostMapping("/github/auth/device/code")
  public Mono<JsonNode> githubDeviceCode() {
    return service.startGitHubDeviceFlow(githubClientId());
  }

  @PostMapping("/github/auth/device/token")
  public Mono<OAuthCredentialResponse> githubDeviceToken(@RequestBody JsonNode body) {
    return IdentityContext.current()
        .flatMap(
            identity ->
                service.pollGitHubDeviceFlow(
                    githubClientId(), required(body, "device_code"), identity));
  }

  @PostMapping("/github/auth/refresh")
  public Mono<OAuthCredentialResponse> githubRefresh(@RequestBody(required = false) JsonNode body) {
    rejectClientToken(body);
    return IdentityContext.current()
        .flatMap(identity -> service.refreshGitHub(githubClientId(), identity));
  }

  @GetMapping("/github/models")
  public Mono<ResponseEntity<JsonNode>> githubModels(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (authorization != null && !authorization.isBlank()) {
      throw new ClientSecretNotAllowedException("Authorization");
    }
    return IdentityContext.current().flatMap(service::githubModels).map(ResponseEntity::ok);
  }

  private String githubClientId() {
    if (githubClientId == null || githubClientId.isBlank()) {
      throw new ProviderOperationException(
          "GITHUB_CLIENT_ID_NOT_CONFIGURED", 503, "GitHub Client ID is not configured");
    }
    return githubClientId;
  }

  private static String required(JsonNode body, String field) {
    if (body == null || !body.hasNonNull(field) || body.path(field).asText().isBlank()) {
      throw io.github.ccweixiao.datastoria.common.error.PlainTextException.badRequest(
          field + " is required");
    }
    return body.path(field).asText();
  }

  private static void rejectClientToken(JsonNode body) {
    if (body != null && (body.has("refresh_token") || body.has("access_token"))) {
      throw new ClientSecretNotAllowedException("refresh_token");
    }
  }
}
