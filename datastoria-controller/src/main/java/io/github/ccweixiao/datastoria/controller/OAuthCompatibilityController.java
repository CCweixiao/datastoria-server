package io.github.ccweixiao.datastoria.controller;

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
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.OAuthCredentialService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ai")
public class OAuthCompatibilityController {

  private final OAuthCredentialService service;

  public OAuthCompatibilityController(OAuthCredentialService service) {
    this.service = service;
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
                    identity));
  }

  @PostMapping("/codex/auth/refresh")
  public Mono<OAuthCredentialResponse> codexRefresh(@RequestBody(required = false) JsonNode body) {
    rejectClientToken(body);
    return IdentityContext.current().flatMap(service::refreshCodex);
  }

  @PostMapping("/github/auth/device/code")
  public Mono<JsonNode> githubDeviceCode() {
    return service.startGitHubDeviceFlow();
  }

  @PostMapping("/github/auth/device/token")
  public Mono<OAuthCredentialResponse> githubDeviceToken(@RequestBody JsonNode body) {
    return IdentityContext.current()
        .flatMap(identity -> service.pollGitHubDeviceFlow(required(body, "device_code"), identity));
  }

  @PostMapping("/github/auth/refresh")
  public Mono<OAuthCredentialResponse> githubRefresh(@RequestBody(required = false) JsonNode body) {
    rejectClientToken(body);
    return IdentityContext.current().flatMap(service::refreshGitHub);
  }

  @GetMapping("/github/models")
  public Mono<ResponseEntity<JsonNode>> githubModels(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    if (authorization != null && !authorization.isBlank()) {
      throw new ClientSecretNotAllowedException("Authorization");
    }
    return IdentityContext.current().flatMap(service::githubModels).map(ResponseEntity::ok);
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
