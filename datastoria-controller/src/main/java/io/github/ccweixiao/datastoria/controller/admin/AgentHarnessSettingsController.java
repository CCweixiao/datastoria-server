package io.github.ccweixiao.datastoria.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.agent.application.AgentHarnessSettingsService;
import io.github.ccweixiao.datastoria.common.dto.AgentHarnessSettingsRequest;
import io.github.ccweixiao.datastoria.common.dto.AgentHarnessSettingsResponse;
import io.github.ccweixiao.datastoria.common.identity.AdminAccess;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.controller.RevisionHeader;

import reactor.core.publisher.Mono;

/**
 * Admin-only endpoints for the tenant-level agent harness runtime overrides (loop bound, tool
 * result eviction, compaction thresholds) stored under {@code settings.ai.agent.harness}. These
 * override the {@code datastoria.agent.*} process defaults for every run in the tenant.
 */
@RestController
@RequestMapping("/api/admin/ai/harness-settings")
@AdminAccess
public class AgentHarnessSettingsController {

  private final AgentHarnessSettingsService service;

  public AgentHarnessSettingsController(AgentHarnessSettingsService service) {
    this.service = service;
  }

  @GetMapping
  public Mono<ResponseEntity<AgentHarnessSettingsResponse>> getSettings() {
    return IdentityContext.current().flatMap(service::current).map(this::withEtag);
  }

  @PutMapping
  public Mono<ResponseEntity<AgentHarnessSettingsResponse>> putSettings(
      @RequestBody AgentHarnessSettingsRequest req,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.update(req, ifMatch, identity))
        .map(this::withEtag);
  }

  private ResponseEntity<AgentHarnessSettingsResponse> withEtag(AgentHarnessSettingsResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
