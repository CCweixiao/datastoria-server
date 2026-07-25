package io.datastoria.server.service;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;

public interface OAuthRemoteClient {

  Mono<JsonNode> postForm(String url, Map<String, String> form);

  Mono<JsonNode> postJson(String url, Map<String, String> body);

  Mono<JsonNode> getGitHubModels(String accessToken);
}
