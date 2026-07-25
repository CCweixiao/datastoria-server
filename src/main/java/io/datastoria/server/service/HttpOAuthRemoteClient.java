package io.datastoria.server.service;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.api.error.ProviderOperationException;

import reactor.core.publisher.Mono;

@Component
public class HttpOAuthRemoteClient implements OAuthRemoteClient {

  private final WebClient client;

  public HttpOAuthRemoteClient(WebClient.Builder builder) {
    this.client =
        builder.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 << 20)).build();
  }

  @Override
  public Mono<JsonNode> postForm(String url, Map<String, String> form) {
    BodyInserters.FormInserter<String> body =
        BodyInserters.fromFormData("grant_type", form.get("grant_type"));
    for (Map.Entry<String, String> entry : form.entrySet()) {
      if (!"grant_type".equals(entry.getKey()) && entry.getValue() != null) {
        body.with(entry.getKey(), entry.getValue());
      }
    }
    return read(
        client
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(body));
  }

  @Override
  public Mono<JsonNode> postJson(String url, Map<String, String> body) {
    return read(
        client
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(body));
  }

  @Override
  public Mono<JsonNode> getGitHubModels(String accessToken) {
    return read(
        client
            .get()
            .uri("https://api.githubcopilot.com/models")
            .header("Authorization", "Bearer " + accessToken)
            .header("Editor-Version", "vscode/1.91.1")
            .header("Editor-Plugin-Version", "copilot-chat/0.17.1")
            .header("User-Agent", "GitHubCopilotChat/0.17.1")
            .accept(MediaType.APPLICATION_JSON));
  }

  private Mono<JsonNode> read(WebClient.RequestHeadersSpec<?> request) {
    return request.exchangeToMono(
        response -> {
          if (response.statusCode().is2xxSuccessful()) {
            return response.bodyToMono(JsonNode.class);
          }
          return response
              .releaseBody()
              .then(
                  Mono.error(
                      new ProviderOperationException(
                          "OAUTH_PROVIDER_ERROR",
                          response.statusCode().value(),
                          "OAuth provider request failed")));
        });
  }
}
