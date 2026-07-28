package io.github.ccweixiao.datastoria.controller.compat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.common.dto.AvailableModelsResponse;
import io.github.ccweixiao.datastoria.common.error.ClientSecretNotAllowedException;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.AvailableModelsService;

import reactor.core.publisher.Mono;

/**
 * A12 compatibility endpoint. Returns the server-managed model catalog so the frontend no longer
 * needs to keep model lists in browser storage. Provider credentials are server-managed.
 */
@RestController
@RequestMapping("/api/ai/models/available")
public class AvailableModelsController {

  private final AvailableModelsService service;

  public AvailableModelsController(AvailableModelsService service) {
    this.service = service;
  }

  @PostMapping
  public Mono<ResponseEntity<AvailableModelsResponse>> getAvailableModels(
      @RequestBody(required = false) JsonNode body) {
    if (body != null) {
      if (body.has("apiKey")) {
        throw new ClientSecretNotAllowedException("apiKey");
      }
      JsonNode github = body.get("github");
      if (github != null && github.has("token")) {
        throw new ClientSecretNotAllowedException("github.token");
      }
    }
    return IdentityContext.current().flatMap(service::getAvailableModels).map(ResponseEntity::ok);
  }
}
