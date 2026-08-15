package io.github.ccweixiao.datastoria.agent.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.ccweixiao.datastoria.common.agent.AgentPendingAction;
import io.github.ccweixiao.datastoria.common.agent.AgentRun;
import io.github.ccweixiao.datastoria.common.agent.AgentRunStatus;
import io.github.ccweixiao.datastoria.common.agent.PendingActionResolution;
import io.github.ccweixiao.datastoria.common.agent.PendingActionStatus;
import io.github.ccweixiao.datastoria.common.agent.PersistedAgentFrame;
import io.github.ccweixiao.datastoria.common.agent.RunTransition;
import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.AgentEventRepository;
import io.github.ccweixiao.datastoria.dao.repository.AgentPendingActionRepository;
import io.github.ccweixiao.datastoria.dao.repository.AgentRunRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Owner-scoped application service behind the P8 run/action APIs. */
@Service
public class AgentRunControlService {

  private final AgentRunRepository runs;
  private final AgentPendingActionRepository actions;
  private final AgentEventRepository events;
  private final AgentRunService runtime;
  private final ObjectMapper mapper;
  private final Scheduler jdbcScheduler;

  public AgentRunControlService(
      AgentRunRepository runs,
      AgentPendingActionRepository actions,
      AgentEventRepository events,
      AgentRunService runtime,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.runs = runs;
    this.actions = actions;
    this.events = events;
    this.runtime = runtime;
    this.mapper = mapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<RunSnapshot> get(Identity identity, String runId) {
    return blocking(() -> snapshot(requireOwned(identity, runId)));
  }

  public Mono<List<PersistedAgentFrame>> events(Identity identity, String runId, long after) {
    if (after < 0) {
      return Mono.error(new IllegalArgumentException("after must be non-negative"));
    }
    return blocking(
        () -> {
          requireOwned(identity, runId);
          return events.findAfter(identity.tenantId(), runId, after);
        });
  }

  public Mono<AgentPendingAction> resolve(
      Identity identity,
      String runId,
      String actionId,
      PendingActionStatus status,
      JsonNode response) {
    return blocking(
        () -> {
          requireOwned(identity, runId);
          String canonical = canonicalResolution(status, response);
          String digest = sha256(canonical);
          return actions.resolve(
              identity.tenantId(),
              identity.userId(),
              runId,
              actionId,
              new PendingActionResolution(
                  status, canonical, digest, identity.userId(), Instant.now()));
        });
  }

  public Mono<RunSnapshot> cancel(Identity identity, String runId) {
    return blocking(
        () -> {
          AgentRun run = requireOwned(identity, runId);
          if (!run.status().isTerminal()) {
            boolean active = runtime.cancel(runId, identity.tenantId(), identity.userId());
            if (!active) {
              runs.transition(
                  identity.tenantId(),
                  runId,
                  AgentRunStatus.CANCELLED,
                  RunTransition.cancelling(Instant.now()));
            }
          }
          // Cascade: a cancelled run must not leave answerable questions behind.
          actions.cancelPendingForRun(
              identity.tenantId(), identity.userId(), runId, identity.userId());
          return snapshot(requireOwned(identity, runId));
        });
  }

  private AgentRun requireOwned(Identity identity, String runId) {
    AgentRun run =
        runs.find(identity.tenantId(), runId)
            .orElseThrow(() -> new NotFoundException("AgentRun", runId));
    if (!identity.userId().equals(run.userId())) {
      throw new NotFoundException("AgentRun", runId);
    }
    return run;
  }

  private RunSnapshot snapshot(AgentRun run) {
    List<AgentPendingAction> pending = actions.findPending(run.tenantId(), run.userId(), run.id());
    return new RunSnapshot(run, pending);
  }

  private String canonicalResolution(PendingActionStatus status, JsonNode response)
      throws Exception {
    JsonNode safeResponse =
        response == null || response.isNull() ? mapper.createObjectNode() : canonicalize(response);
    var root = mapper.createObjectNode();
    root.put("status", status.dbValue());
    root.set("response", safeResponse);
    return mapper.writeValueAsString(root);
  }

  private JsonNode canonicalize(JsonNode value) {
    if (value.isObject()) {
      ObjectNode sorted = mapper.createObjectNode();
      java.util.stream.StreamSupport.stream(
              java.util.Spliterators.spliteratorUnknownSize(
                  value.fieldNames(), java.util.Spliterator.ORDERED),
              false)
          .sorted()
          .forEach(name -> sorted.set(name, canonicalize(value.get(name))));
      return sorted;
    }
    if (value.isArray()) {
      ArrayNode array = mapper.createArrayNode();
      value.forEach(item -> array.add(canonicalize(item)));
      return array;
    }
    return value.deepCopy();
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private <T> Mono<T> blocking(ThrowingSupplier<T> supplier) {
    return Mono.fromCallable(supplier::get).subscribeOn(jdbcScheduler);
  }

  public record RunSnapshot(AgentRun run, List<AgentPendingAction> pendingActions) {}

  @FunctionalInterface
  private interface ThrowingSupplier<T> {
    T get() throws Exception;
  }
}
