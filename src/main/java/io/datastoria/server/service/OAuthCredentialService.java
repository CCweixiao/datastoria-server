package io.datastoria.server.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.PlainTextException;
import io.datastoria.server.api.error.ProviderOperationException;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.OAuthCredential;
import io.datastoria.server.domain.Secret;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.dto.OAuthCredentialResponse;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.OAuthCredentialRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class OAuthCredentialService {

  private static final String CODEX_TOKEN_URL = "https://auth.openai.com/oauth/token";

  private final OAuthCredentialRepository credentials;
  private final SecretService secrets;
  private final OAuthRemoteClient remote;
  private final ObjectMapper mapper;
  private final TransactionTemplate transactions;
  private final Scheduler jdbcScheduler;

  @Value("${datastoria.oauth.codex.client-id:app_EMoamEEZ73f0CkXaXp7hrann}")
  private String codexClientId;

  @Value("${datastoria.oauth.github.client-id:}")
  private String githubClientId;

  public OAuthCredentialService(
      OAuthCredentialRepository credentials,
      SecretService secrets,
      OAuthRemoteClient remote,
      ObjectMapper mapper,
      TransactionTemplate transactions,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.credentials = credentials;
    this.secrets = secrets;
    this.remote = remote;
    this.mapper = mapper;
    this.transactions = transactions;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<OAuthCredentialResponse> exchangeCodex(
      String code,
      String verifier,
      String redirectUri,
      String clientId,
      String tokenUrl,
      Identity identity) {
    return remote
        .postForm(
            tokenUrl,
            Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "code", code,
                "code_verifier", verifier,
                "redirect_uri", redirectUri))
        .flatMap(payload -> persist("codex", payload, identity));
  }

  public Mono<OAuthCredentialResponse> refreshCodex(
      String clientId, String tokenUrl, Identity identity) {
    return loadBundle("codex", identity)
        .flatMap(
            existing -> {
              String refreshToken = text(existing.payload(), "refresh_token");
              return remote
                  .postForm(
                      tokenUrl,
                      Map.of(
                          "grant_type", "refresh_token",
                          "client_id", clientId,
                          "refresh_token", refreshToken))
                  .map(payload -> mergeRefresh(payload, refreshToken));
            })
        .flatMap(payload -> persist("codex", payload, identity));
  }

  public Mono<JsonNode> startGitHubDeviceFlow(String clientId) {
    return remote.postJson(
        "https://github.com/login/device/code",
        Map.of("client_id", clientId, "scope", "read:user"));
  }

  public Mono<OAuthCredentialResponse> pollGitHubDeviceFlow(
      String clientId, String deviceCode, Identity identity) {
    return remote
        .postJson(
            "https://github.com/login/oauth/access_token",
            Map.of(
                "client_id", clientId,
                "device_code", deviceCode,
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code"))
        .flatMap(payload -> persist("github", payload, identity));
  }

  public Mono<OAuthCredentialResponse> refreshGitHub(String clientId, Identity identity) {
    return loadBundle("github", identity)
        .flatMap(
            existing -> {
              String refreshToken = text(existing.payload(), "refresh_token");
              return remote
                  .postJson(
                      "https://github.com/login/oauth/access_token",
                      Map.of(
                          "client_id", clientId,
                          "refresh_token", refreshToken,
                          "grant_type", "refresh_token"))
                  .map(payload -> mergeRefresh(payload, refreshToken));
            })
        .flatMap(payload -> persist("github", payload, identity));
  }

  public Mono<JsonNode> githubModels(Identity identity) {
    return loadBundle("github", identity)
        .flatMap(bundle -> remote.getGitHubModels(text(bundle.payload(), "access_token")));
  }

  /** Checks owner-scoped OAuth configuration without decrypting or returning token material. */
  public boolean hasCredential(String provider, Identity identity) {
    return credentials.findByOwner(identity.tenantId(), identity.userId(), provider).isPresent();
  }

  /** Returns a decrypted access token only to the server-side model adapter boundary. */
  public String accessToken(String provider, Identity identity) {
    OAuthCredential credential = credential(provider, identity);
    if (credential.expiresAt() != null
        && !credential.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
      refreshForRuntime(provider, identity);
      credential = credential(provider, identity);
    }
    try {
      return text(
          mapper.readTree(secrets.decrypt(credential.secretId(), identity.tenantId())),
          "access_token");
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new IllegalStateException("Stored OAuth credential is invalid", exception);
    }
  }

  private OAuthCredential credential(String provider, Identity identity) {
    return credentials
        .findByOwner(identity.tenantId(), identity.userId(), provider)
        .orElseThrow(() -> new NotFoundException("OAuthCredential", provider));
  }

  private void refreshForRuntime(String provider, Identity identity) {
    if ("codex".equals(provider)) {
      refreshCodex(codexClientId, CODEX_TOKEN_URL, identity).block(Duration.ofSeconds(15));
      return;
    }
    if ("github".equals(provider) && githubClientId != null && !githubClientId.isBlank()) {
      refreshGitHub(githubClientId, identity).block(Duration.ofSeconds(15));
      return;
    }
    throw new ProviderOperationException(
        "OAUTH_REFRESH_UNAVAILABLE", 503, "OAuth credential refresh is unavailable");
  }

  private Mono<OAuthCredentialResponse> persist(
      String provider, JsonNode payload, Identity identity) {
    if (payload.hasNonNull("error")) {
      throw PlainTextException.badRequest(
          payload.path("error").asText("OAuth authorization failed"));
    }
    text(payload, "access_token");
    return Mono.fromCallable(() -> persistBlocking(provider, payload, identity))
        .subscribeOn(jdbcScheduler);
  }

  private OAuthCredentialResponse persistBlocking(
      String provider, JsonNode payload, Identity identity) {
    return transactions.execute(
        status -> {
          Instant now = Instant.now();
          long expiresIn = payload.path("expires_in").asLong(0);
          Instant expiresAt = expiresIn > 0 ? now.plusSeconds(expiresIn) : null;
          String serialized;
          try {
            serialized = mapper.writeValueAsString(payload);
          } catch (Exception exception) {
            throw new IllegalStateException("OAuth token payload serialization failed", exception);
          }
          Secret saved =
              secrets.save(
                  identity.tenantId(), identity.userId(), "access_token", serialized, expiresAt);
          OAuthCredential existing =
              credentials
                  .findByOwner(identity.tenantId(), identity.userId(), provider)
                  .orElse(null);
          OAuthCredential stored;
          if (existing == null) {
            stored =
                credentials.save(
                    new OAuthCredential(
                        Ulid.next(),
                        identity.tenantId(),
                        identity.userId(),
                        provider,
                        saved.id(),
                        nullableText(payload, "token_type"),
                        nullableText(payload, "scope"),
                        expiresAt,
                        0,
                        now,
                        now));
          } else {
            stored =
                credentials.update(
                    new OAuthCredential(
                        existing.id(),
                        existing.tenantId(),
                        existing.userId(),
                        existing.providerKey(),
                        saved.id(),
                        nullableText(payload, "token_type"),
                        nullableText(payload, "scope"),
                        expiresAt,
                        existing.revision(),
                        existing.createdAt(),
                        now),
                    existing.revision());
            secrets.delete(existing.secretId(), identity.tenantId());
          }
          return response(stored);
        });
  }

  private Mono<TokenBundle> loadBundle(String provider, Identity identity) {
    return Mono.fromCallable(
            () -> {
              OAuthCredential credential =
                  credentials
                      .findByOwner(identity.tenantId(), identity.userId(), provider)
                      .orElseThrow(() -> new NotFoundException("OAuthCredential", provider));
              try {
                return new TokenBundle(
                    credential,
                    mapper.readTree(secrets.decrypt(credential.secretId(), identity.tenantId())));
              } catch (Exception exception) {
                throw new IllegalStateException("Stored OAuth credential is invalid", exception);
              }
            })
        .subscribeOn(jdbcScheduler);
  }

  private JsonNode mergeRefresh(JsonNode payload, String existingRefreshToken) {
    if (payload.hasNonNull("refresh_token")) {
      return payload;
    }
    if (!payload.isObject()) {
      return payload;
    }
    ((com.fasterxml.jackson.databind.node.ObjectNode) payload)
        .put("refresh_token", existingRefreshToken);
    return payload;
  }

  private OAuthCredentialResponse response(OAuthCredential credential) {
    return new OAuthCredentialResponse(
        credential.providerKey(),
        true,
        credential.tokenType(),
        credential.scope(),
        credential.expiresAt());
  }

  private static String text(JsonNode node, String field) {
    String value = nullableText(node, field);
    if (value == null || value.isBlank()) {
      throw PlainTextException.badRequest(field + " is missing from OAuth provider response");
    }
    return value;
  }

  private static String nullableText(JsonNode node, String field) {
    return node.hasNonNull(field) ? node.path(field).asText() : null;
  }

  private record TokenBundle(OAuthCredential credential, JsonNode payload) {}
}
