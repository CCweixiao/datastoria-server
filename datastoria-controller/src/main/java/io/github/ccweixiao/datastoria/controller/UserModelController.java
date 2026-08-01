package io.github.ccweixiao.datastoria.controller;

import java.util.List;

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

import io.github.ccweixiao.datastoria.common.dto.UserModelRequest;
import io.github.ccweixiao.datastoria.common.dto.UserModelResponse;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.UserModelService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/** Private model configurations for the authenticated user. */
@RestController
@RequestMapping("/api/me/ai/models")
public class UserModelController {

  private final UserModelService service;

  public UserModelController(UserModelService service) {
    this.service = service;
  }

  @GetMapping
  public Mono<ResponseEntity<List<UserModelResponse>>> list() {
    return IdentityContext.current().flatMap(service::findAll).map(ResponseEntity::ok);
  }

  @PostMapping
  public Mono<ResponseEntity<UserModelResponse>> create(
      @RequestBody @Valid UserModelRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.create(request, identity))
        .map(this::withEtag);
  }

  @PutMapping("/{id}")
  public Mono<ResponseEntity<UserModelResponse>> update(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @RequestBody @Valid UserModelRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.update(id, RevisionHeader.parse(ifMatch), request, identity))
        .map(this::withEtag);
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> delete(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    return IdentityContext.current()
        .flatMap(identity -> service.delete(id, RevisionHeader.parse(ifMatch), identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  private ResponseEntity<UserModelResponse> withEtag(UserModelResponse response) {
    return ResponseEntity.ok().eTag(String.valueOf(response.revision())).body(response);
  }
}
