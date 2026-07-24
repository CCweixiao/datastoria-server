package io.datastoria.server.api.compat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.api.error.ClientSecretNotAllowedException;
import io.datastoria.server.dto.AvailableModelsResponse;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.AvailableModelsService;

import reactor.core.publisher.Mono;

/**
 * A12 compatibility endpoint. Returns the server-managed model catalog so the frontend no longer
 * needs to keep model lists in localStorage. Rejects bodies carrying {@code apiKey}; ignores (with
 * warning) the legacy {@code github.token} field until P10 OAuth lands.
 */
@RestController
@RequestMapping("/api/ai/models/available")
public class AvailableModelsController {

  private static final Logger log = LoggerFactory.getLogger(AvailableModelsController.class);

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
        log.warn(
            "github.token received on /api/ai/models/available — ignored until P10 OAuth."
                + " Caller should migrate to server-side token flow.");
      }
    }
    return IdentityContext.current().flatMap(service::getAvailableModels).map(ResponseEntity::ok);
  }
}
