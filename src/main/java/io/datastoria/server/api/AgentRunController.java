package io.datastoria.server.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.agent.application.AgentRunControlService;
import io.datastoria.server.agent.application.AgentRunControlService.RunSnapshot;
import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.PendingActionStatus;
import io.datastoria.server.agent.domain.PersistedAgentFrame;
import io.datastoria.server.identity.IdentityContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

/** P8 owner-scoped run inspection, event replay, action resolution, and cancellation API. */
@RestController
@RequestMapping("/api/ai/runs")
public class AgentRunController {

  private final AgentRunControlService service;

  public AgentRunController(AgentRunControlService service) {
    this.service = service;
  }

  @GetMapping("/{runId}")
  public Mono<RunSnapshot> get(@PathVariable String runId) {
    return IdentityContext.current().flatMap(identity -> service.get(identity, runId));
  }

  @GetMapping("/{runId}/events")
  public Mono<List<PersistedAgentFrame>> events(
      @PathVariable String runId, @RequestParam(defaultValue = "0") long after) {
    return IdentityContext.current().flatMap(identity -> service.events(identity, runId, after));
  }

  @PostMapping("/{runId}/actions/{actionId}:respond")
  public Mono<AgentPendingAction> respond(
      @PathVariable String runId,
      @PathVariable String actionId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody ResolutionBody body) {
    requireIdempotencyKey(idempotencyKey);
    return IdentityContext.current()
        .flatMap(
            identity ->
                service.resolve(
                    identity, runId, actionId, PendingActionStatus.RESPONDED, body.response()));
  }

  @PostMapping("/{runId}/actions/{actionId}:approve")
  public Mono<AgentPendingAction> approve(
      @PathVariable String runId,
      @PathVariable String actionId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) JsonNode body) {
    requireIdempotencyKey(idempotencyKey);
    return IdentityContext.current()
        .flatMap(
            identity ->
                service.resolve(identity, runId, actionId, PendingActionStatus.APPROVED, body));
  }

  @PostMapping("/{runId}/actions/{actionId}:deny")
  public Mono<AgentPendingAction> deny(
      @PathVariable String runId,
      @PathVariable String actionId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody(required = false) JsonNode body) {
    requireIdempotencyKey(idempotencyKey);
    return IdentityContext.current()
        .flatMap(
            identity ->
                service.resolve(identity, runId, actionId, PendingActionStatus.DENIED, body));
  }

  @PostMapping("/{runId}:cancel")
  @ResponseStatus(HttpStatus.OK)
  public Mono<RunSnapshot> cancel(
      @PathVariable String runId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    requireIdempotencyKey(idempotencyKey);
    return IdentityContext.current().flatMap(identity -> service.cancel(identity, runId));
  }

  private static void requireIdempotencyKey(String value) {
    if (value == null || value.isBlank() || value.length() > 128) {
      throw new IllegalArgumentException("A valid Idempotency-Key is required");
    }
  }

  public record ResolutionBody(@NotNull JsonNode response) {}
}
