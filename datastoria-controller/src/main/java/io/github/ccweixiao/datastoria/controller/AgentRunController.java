package io.github.ccweixiao.datastoria.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ccweixiao.datastoria.agent.application.AgentEventReplayService;
import io.github.ccweixiao.datastoria.agent.application.AgentRunControlService;
import io.github.ccweixiao.datastoria.agent.application.AgentRunControlService.RunSnapshot;
import io.github.ccweixiao.datastoria.agent.application.ChatRunService;
import io.github.ccweixiao.datastoria.common.agent.AgentPendingAction;
import io.github.ccweixiao.datastoria.common.agent.PendingActionStatus;
import io.github.ccweixiao.datastoria.common.agent.PersistedAgentFrame;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

/** P8 owner-scoped run inspection, event replay, action resolution, and cancellation API. */
@RestController
@RequestMapping("/api/ai/runs")
public class AgentRunController {

  private final AgentRunControlService service;
  private final ChatRunService chatRuns;
  private final AgentEventReplayService replay;

  public AgentRunController(
      AgentRunControlService service, ChatRunService chatRuns, AgentEventReplayService replay) {
    this.service = service;
    this.chatRuns = chatRuns;
    this.replay = replay;
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

  @PostMapping("/{runId}:resume")
  public Mono<Void> resume(
      @PathVariable String runId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      ServerWebExchange exchange) {
    requireIdempotencyKey(idempotencyKey);
    return IdentityContext.current()
        .flatMap(
            identity ->
                chatRuns
                    .resume(runId, identity)
                    .flatMap(
                        events ->
                            writeSse(
                                exchange,
                                replay.encodeAndRecord(identity.tenantId(), events, null))));
  }

  private static void requireIdempotencyKey(String value) {
    if (value == null || value.isBlank() || value.length() > 128) {
      throw new IllegalArgumentException("A valid Idempotency-Key is required");
    }
  }

  private static Mono<Void> writeSse(
      ServerWebExchange exchange, reactor.core.publisher.Flux<String> frames) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.OK);
    HttpHeaders headers = response.getHeaders();
    headers.setContentType(MediaType.TEXT_EVENT_STREAM);
    headers.add("Cache-Control", "no-cache");
    headers.add("Connection", "keep-alive");
    headers.add("X-Vercel-AI-UI-Message-Stream", "v1");
    headers.add("X-Accel-Buffering", "no");
    return response.writeWith(
        frames.map(frame -> response.bufferFactory().wrap(frame.getBytes(StandardCharsets.UTF_8))));
  }

  public record ResolutionBody(@NotNull JsonNode response) {}
}
