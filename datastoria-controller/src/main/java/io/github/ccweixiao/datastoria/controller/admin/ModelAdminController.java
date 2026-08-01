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

import io.github.ccweixiao.datastoria.common.dto.CreateModelRequest;
import io.github.ccweixiao.datastoria.common.dto.ModelResponse;
import io.github.ccweixiao.datastoria.common.dto.UpdateModelRequest;
import io.github.ccweixiao.datastoria.common.identity.AdminAccess;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.controller.RevisionHeader;
import io.github.ccweixiao.datastoria.service.ModelService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/** Admin API for model catalog management. */
@RestController
@RequestMapping("/api/admin/ai/models")
@AdminAccess
public class ModelAdminController {

  private static final Logger log = LoggerFactory.getLogger(ModelAdminController.class);

  private final ModelService modelService;

  public ModelAdminController(ModelService modelService) {
    this.modelService = modelService;
  }

  @GetMapping
  public Mono<ResponseEntity<List<ModelResponse>>> listModels() {
    return IdentityContext.current()
        .flatMap(identity -> modelService.findAll(identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping
  public Mono<ResponseEntity<ModelResponse>> createModel(
      @RequestBody @Valid CreateModelRequest req) {
    return IdentityContext.current()
        .flatMap(identity -> modelService.create(req, identity))
        .map(this::withEtag);
  }

  @GetMapping("/{id}")
  public Mono<ResponseEntity<ModelResponse>> getModel(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> modelService.findById(id, identity))
        .map(this::withEtag);
  }

  @PutMapping("/{id}")
  public Mono<ResponseEntity<ModelResponse>> updateModel(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader,
      @RequestBody @Valid UpdateModelRequest req) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    if (ifMatch == null) {
      log.warn("PUT /api/admin/ai/models/{} without If-Match — last-write-wins", id);
    }
    return IdentityContext.current()
        .flatMap(identity -> modelService.update(id, ifMatch, req, identity))
        .map(this::withEtag);
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> deleteModel(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> modelService.delete(id, ifMatch, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  private ResponseEntity<ModelResponse> withEtag(ModelResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
