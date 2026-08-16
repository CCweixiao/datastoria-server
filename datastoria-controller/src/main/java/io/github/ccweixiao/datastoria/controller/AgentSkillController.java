package io.github.ccweixiao.datastoria.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.agent.service.AgentSkillService;
import io.github.ccweixiao.datastoria.common.dto.CommandResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillCatalogResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillDetailResponse;
import io.github.ccweixiao.datastoria.common.dto.SkillResourceResponse;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;

import reactor.core.publisher.Mono;

/**
 * Read-only Skill catalog. Skills ship with the jar (classpath) and cannot be created, edited or
 * deleted through the API; IdentityContext is still required so only authenticated users read it.
 */
@RestController
@RequestMapping("/api/ai")
public class AgentSkillController {

  private final AgentSkillService service;

  public AgentSkillController(AgentSkillService service) {
    this.service = service;
  }

  @GetMapping("/skills")
  public Mono<ResponseEntity<List<SkillCatalogResponse>>> list() {
    return IdentityContext.current().map(identity -> ResponseEntity.ok(service.list()));
  }

  @GetMapping("/skills/{id}")
  public Mono<ResponseEntity<SkillDetailResponse>> detail(@PathVariable String id) {
    return IdentityContext.current().map(identity -> ResponseEntity.ok(service.detail(id)));
  }

  @GetMapping("/skills/{id}/resource")
  public Mono<ResponseEntity<SkillResourceResponse>> resource(
      @PathVariable String id, @RequestParam String path) {
    return IdentityContext.current().map(identity -> ResponseEntity.ok(service.resource(id, path)));
  }

  @GetMapping("/commands")
  public Mono<ResponseEntity<List<CommandResponse>>> commands() {
    return IdentityContext.current().map(identity -> ResponseEntity.ok(service.commands()));
  }
}
