package io.github.ccweixiao.datastoria.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.dto.AvailableProviderResponse;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.AvailableModelsService;

import reactor.core.publisher.Mono;

/** Sanitized provider choices for users configuring private models. */
@RestController
@RequestMapping("/api/ai/providers/available")
public class AvailableProvidersController {

  private final AvailableModelsService service;

  public AvailableProvidersController(AvailableModelsService service) {
    this.service = service;
  }

  @GetMapping
  public Mono<ResponseEntity<List<AvailableProviderResponse>>> list() {
    return IdentityContext.current()
        .flatMap(service::getAvailableProviders)
        .map(ResponseEntity::ok);
  }
}
