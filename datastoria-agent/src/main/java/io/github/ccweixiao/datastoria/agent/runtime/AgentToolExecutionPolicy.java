package io.github.ccweixiao.datastoria.agent.runtime;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.ccweixiao.datastoria.common.domain.AuditLog;
import io.github.ccweixiao.datastoria.common.error.ProviderOperationException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.AuditLogRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Run-scoped timeout, concurrency, output-cap, and audit policy for server-side tools. */
public final class AgentToolExecutionPolicy {

  private static final Logger log = LoggerFactory.getLogger(AgentToolExecutionPolicy.class);
  static final int MAX_OUTPUT_CHARS = 256_000;
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(35);

  private final Semaphore permits;
  private final AuditLogRepository auditRepository;
  private final Scheduler auditScheduler;
  private final Identity identity;
  private final String runId;
  private final String connectionId;
  private final Duration timeout;

  private AgentToolExecutionPolicy(
      int maxConcurrency,
      AuditLogRepository auditRepository,
      Scheduler auditScheduler,
      Identity identity,
      String runId,
      String connectionId,
      Duration timeout) {
    this.permits = new Semaphore(maxConcurrency);
    this.auditRepository = auditRepository;
    this.auditScheduler = auditScheduler;
    this.identity = identity;
    this.runId = runId;
    this.connectionId = connectionId;
    this.timeout = timeout;
  }

  public static AgentToolExecutionPolicy tracked(
      AuditLogRepository auditRepository,
      Scheduler auditScheduler,
      Identity identity,
      String runId,
      String connectionId) {
    return new AgentToolExecutionPolicy(
        2, auditRepository, auditScheduler, identity, runId, connectionId, DEFAULT_TIMEOUT);
  }

  static AgentToolExecutionPolicy untracked() {
    return untracked(DEFAULT_TIMEOUT);
  }

  static AgentToolExecutionPolicy untracked(Duration timeout) {
    return new AgentToolExecutionPolicy(2, null, null, null, null, null, timeout);
  }

  public Mono<String> guard(String toolName, Mono<String> operation) {
    return Mono.defer(
        () -> {
          if (!permits.tryAcquire()) {
            ProviderOperationException error =
                new ProviderOperationException(
                    "AGENT_TOOL_BUSY", 429, "Too many concurrent tool calls");
            return audit(toolName, "failure").then(Mono.error(error));
          }
          return operation
              .timeout(timeout)
              .onErrorMap(
                  TimeoutException.class,
                  error ->
                      new ProviderOperationException(
                          "AGENT_TOOL_TIMEOUT", 504, "Agent tool call timed out"))
              .map(AgentToolExecutionPolicy::enforceCap)
              .flatMap(output -> audit(toolName, "success").thenReturn(output))
              .onErrorResume(error -> audit(toolName, "failure").then(Mono.<String>error(error)))
              .doFinally(ignored -> permits.release());
        });
  }

  private Mono<Void> audit(String toolName, String result) {
    if (auditRepository == null) {
      return Mono.empty();
    }
    return Mono.fromRunnable(
            () ->
                auditRepository.save(
                    new AuditLog(
                        null,
                        identity.tenantId(),
                        identity.userId(),
                        "agent.tool.execute",
                        "agent_run",
                        runId,
                        runId,
                        "{\"tool\":\""
                            + safe(toolName)
                            + "\",\"connectionId\":\""
                            + safe(connectionId)
                            + "\"}",
                        result,
                        null)))
        .subscribeOn(auditScheduler)
        .doOnError(error -> log.warn("Unable to persist agent tool audit for run {}", runId))
        .onErrorResume(ignored -> Mono.empty())
        .then();
  }

  private static String enforceCap(String output) {
    if (output == null || output.length() <= MAX_OUTPUT_CHARS) {
      return output;
    }
    throw new ProviderOperationException(
        "AGENT_TOOL_OUTPUT_TOO_LARGE", 413, "Agent tool output exceeded the server limit");
  }

  private static String safe(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
