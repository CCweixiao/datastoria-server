package io.datastoria.server.api.admin;

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

import io.datastoria.server.api.RevisionHeader;
import io.datastoria.server.dto.AgentDefinitionResponse;
import io.datastoria.server.dto.AgentRevisionResponse;
import io.datastoria.server.dto.CreateAgentRequest;
import io.datastoria.server.dto.CreateAgentRevisionRequest;
import io.datastoria.server.dto.UpdateAgentRequest;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.AgentDefinitionService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/** Admin API for agent definition and revision management. */
@RestController
@RequestMapping("/api/admin/ai/agents")
public class AgentAdminController {

  private final AgentDefinitionService agentService;

  public AgentAdminController(AgentDefinitionService agentService) {
    this.agentService = agentService;
  }

  @GetMapping
  public Mono<ResponseEntity<List<AgentDefinitionResponse>>> listAgents() {
    return IdentityContext.current().flatMap(agentService::findAll).map(ResponseEntity::ok);
  }

  @PostMapping
  public Mono<ResponseEntity<AgentDefinitionResponse>> createAgent(
      @RequestBody @Valid CreateAgentRequest req) {
    return IdentityContext.current()
        .flatMap(identity -> agentService.create(req, identity))
        .map(this::withEtag);
  }

  @GetMapping("/{id}")
  public Mono<ResponseEntity<AgentDefinitionResponse>> getAgent(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> agentService.findById(id, identity))
        .map(this::withEtag);
  }

  @PostMapping("/{id}/revisions")
  public Mono<ResponseEntity<AgentRevisionResponse>> createRevision(
      @PathVariable String id, @RequestBody @Valid CreateAgentRevisionRequest req) {
    return IdentityContext.current()
        .flatMap(identity -> agentService.createRevision(id, req, identity))
        .map(ResponseEntity::ok);
  }

  @PutMapping("/{id}")
  public Mono<ResponseEntity<AgentDefinitionResponse>> updateAgent(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader,
      @RequestBody @Valid UpdateAgentRequest req) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> agentService.update(id, req, ifMatch, identity))
        .map(this::withEtag);
  }

  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<Void>> deleteAgent(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> agentService.delete(id, ifMatch, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @PostMapping("/{id}/revisions/{revisionId}:publish")
  public Mono<ResponseEntity<AgentDefinitionResponse>> publishRevision(
      @PathVariable String id,
      @PathVariable String revisionId,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> agentService.publish(id, revisionId, ifMatch, identity))
        .map(this::withEtag);
  }

  @PostMapping("/{id}:disable")
  public Mono<ResponseEntity<AgentDefinitionResponse>> disableAgent(
      @PathVariable String id,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> agentService.disable(id, ifMatch, identity))
        .map(this::withEtag);
  }

  private ResponseEntity<AgentDefinitionResponse> withEtag(AgentDefinitionResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
