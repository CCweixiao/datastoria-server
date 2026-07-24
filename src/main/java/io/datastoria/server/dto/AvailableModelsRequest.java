package io.datastoria.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Request body for POST /api/ai/models/available. The {@code github.token} field is accepted for
 * backward-compat with the browser but is ignored server-side (logged as a security warning).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AvailableModelsRequest(GithubSection github) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GithubSection(String token) {}
}
