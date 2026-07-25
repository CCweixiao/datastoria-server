package io.datastoria.server.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.datastoria.server.dto.CommandResponse;
import io.datastoria.server.dto.SkillCatalogResponse;
import io.datastoria.server.dto.SkillDetailResponse;
import io.datastoria.server.dto.SkillResourceResponse;
import io.datastoria.server.dto.UpsertSkillRequest;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.AgentSkillService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ai")
public class AgentSkillController {

  private final AgentSkillService service;

  public AgentSkillController(AgentSkillService service) {
    this.service = service;
  }

  @GetMapping("/skills")
  public Mono<ResponseEntity<List<SkillCatalogResponse>>> list(
      @RequestParam(defaultValue = "false") boolean includeDraft) {
    return IdentityContext.current()
        .flatMap(identity -> service.list(identity, includeDraft))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/skills")
  public Mono<ResponseEntity<Map<String, Boolean>>> create(
      @RequestBody @Valid UpsertSkillRequest request) {
    if (request.id() == null || request.content() == null || request.content().isBlank()) {
      return Mono.error(new IllegalArgumentException("Missing required skill fields"));
    }
    return IdentityContext.current()
        .flatMap(identity -> service.upsert(request.id(), request, identity))
        .thenReturn(ResponseEntity.status(201).body(Map.of("ok", true)));
  }

  @GetMapping("/skills/{id}")
  public Mono<ResponseEntity<SkillDetailResponse>> detail(
      @PathVariable String id, @RequestParam(defaultValue = "false") boolean includeDraft) {
    return IdentityContext.current()
        .flatMap(identity -> service.detail(id, identity, includeDraft))
        .map(ResponseEntity::ok);
  }

  @PatchMapping("/skills/{id}")
  public Mono<ResponseEntity<Map<String, Boolean>>> update(
      @PathVariable String id, @RequestBody @Valid UpsertSkillRequest request) {
    return IdentityContext.current()
        .flatMap(
            identity ->
                request.action() != null
                        && (request.content() == null || request.content().isBlank())
                    ? service.publish(id, identity)
                    : requireContentThenUpsert(id, request, identity))
        .thenReturn(ResponseEntity.ok(Map.of("ok", true)));
  }

  @DeleteMapping("/skills/{id}")
  public Mono<ResponseEntity<Map<String, Boolean>>> delete(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> service.delete(id, identity))
        .thenReturn(ResponseEntity.ok(Map.of("ok", true)));
  }

  @GetMapping("/skills/{id}/resource")
  public Mono<ResponseEntity<SkillResourceResponse>> resource(
      @PathVariable String id,
      @RequestParam String path,
      @RequestParam(defaultValue = "false") boolean includeDraft) {
    return IdentityContext.current()
        .flatMap(identity -> service.resource(id, path, identity, includeDraft))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/commands")
  public Mono<ResponseEntity<List<CommandResponse>>> commands() {
    return IdentityContext.current().flatMap(service::commands).map(ResponseEntity::ok);
  }

  private Mono<Void> requireContentThenUpsert(
      String id, UpsertSkillRequest request, io.datastoria.server.identity.Identity identity) {
    if (request.content() == null || request.content().isBlank()) {
      return Mono.error(new IllegalArgumentException("Missing required skill fields"));
    }
    return service.upsert(id, request, identity);
  }
}
