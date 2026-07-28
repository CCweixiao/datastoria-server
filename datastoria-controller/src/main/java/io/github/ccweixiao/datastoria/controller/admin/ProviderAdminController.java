package io.github.ccweixiao.datastoria.controller.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.dto.CreateProviderRequest;
import io.github.ccweixiao.datastoria.common.dto.CredentialRequest;
import io.github.ccweixiao.datastoria.common.dto.CredentialResponse;
import io.github.ccweixiao.datastoria.common.dto.DiscoveredModelResponse;
import io.github.ccweixiao.datastoria.common.dto.ProviderResponse;
import io.github.ccweixiao.datastoria.common.dto.ProviderTestResponse;
import io.github.ccweixiao.datastoria.common.dto.UpdateProviderRequest;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.controller.RevisionHeader;
import io.github.ccweixiao.datastoria.service.ProviderService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/**
 * Admin API for model provider configuration. Credential write endpoints never log the request
 * body.
 */
@RestController
@RequestMapping("/api/admin/ai/providers")
public class ProviderAdminController {

  private static final Logger log = LoggerFactory.getLogger(ProviderAdminController.class);

  private final ProviderService providerService;

  public ProviderAdminController(ProviderService providerService) {
    this.providerService = providerService;
  }

  @GetMapping
  public Mono<ResponseEntity<List<ProviderResponse>>> listProviders() {
    return IdentityContext.current()
        .flatMap(identity -> providerService.findAll(identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping
  public Mono<ResponseEntity<ProviderResponse>> createProvider(
      @RequestBody @Valid CreateProviderRequest req) {
    return IdentityContext.current()
        .flatMap(identity -> providerService.create(req, identity))
        .map(this::withEtag);
  }

  @GetMapping("/{id}")
  public Mono<ResponseEntity<ProviderResponse>> getProvider(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> providerService.findById(id, identity))
        .map(this::withEtag);
  }

  @PutMapping("/{id}")
  public Mono<ResponseEntity<ProviderResponse>> updateProvider(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader,
      @RequestBody @Valid UpdateProviderRequest req) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    if (ifMatch == null) {
      log.warn("PUT /api/admin/ai/providers/{} without If-Match — last-write-wins", id);
    }
    return IdentityContext.current()
        .flatMap(identity -> providerService.update(id, ifMatch, req, identity))
        .map(this::withEtag);
  }

  @PutMapping("/{id}/credential")
  public Mono<ResponseEntity<CredentialResponse>> putCredential(
      @PathVariable String id,
      // Body is intentionally NOT logged; see logback config / @JsonRawValue redaction
      @RequestBody @Valid CredentialRequest req) {
    return IdentityContext.current()
        .flatMap(identity -> providerService.putCredential(id, req, identity))
        .map(ResponseEntity::ok);
  }

  @DeleteMapping("/{id}/credential")
  public Mono<ResponseEntity<Void>> deleteCredential(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> providerService.deleteCredential(id, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> deleteProvider(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> providerService.delete(id, ifMatch, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @PostMapping("/{id}:test")
  public Mono<ResponseEntity<ProviderTestResponse>> testProvider(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> providerService.testConnection(id, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/models:discover")
  public Mono<ResponseEntity<List<DiscoveredModelResponse>>> discoverModels(
      @PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> providerService.discoverModels(id, identity))
        .map(ResponseEntity::ok);
  }

  private ResponseEntity<ProviderResponse> withEtag(ProviderResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
