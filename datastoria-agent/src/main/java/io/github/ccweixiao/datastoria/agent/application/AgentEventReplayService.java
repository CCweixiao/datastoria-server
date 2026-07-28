package io.github.ccweixiao.datastoria.agent.application;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.agent.AgentRunEvent;
import io.github.ccweixiao.datastoria.common.agent.PersistedAgentFrame;
import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.AgentEventRepository;
import io.github.ccweixiao.datastoria.dao.repository.AgentRunRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Records and replays the exact SSE frames emitted for a run. */
@Service
public class AgentEventReplayService {

  private final AgentEventRepository eventRepository;
  private final AgentRunRepository runRepository;
  private final Scheduler jdbcScheduler;

  public AgentEventReplayService(
      AgentEventRepository eventRepository,
      AgentRunRepository runRepository,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.eventRepository = eventRepository;
    this.runRepository = runRepository;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Flux<String> encodeAndRecord(String tenantId, Flux<AgentRunEvent> events, String title) {
    return encodeAndRecord(tenantId, events, Mono.justOrEmpty(title), title);
  }

  public Flux<String> encodeAndRecord(
      String tenantId,
      Flux<AgentRunEvent> events,
      Mono<String> generatedTitle,
      String fallbackTitle) {
    return Flux.defer(
        () -> {
          AiSdkStreamEncoder encoder = new AiSdkStreamEncoder().withTitle(fallbackTitle);
          AtomicLong sequence = new AtomicLong(-1L);
          AtomicReference<String> runId = new AtomicReference<>();
          Flux<String> frames =
              events.concatMap(
                  event -> {
                    runId.compareAndSet(null, event.runId());
                    if (event instanceof AgentRunEvent.RunCompleted) {
                      return generatedTitle
                          .defaultIfEmpty(fallbackTitle == null ? "" : fallbackTitle)
                          .flatMapIterable(
                              title -> {
                                encoder.withTitle(title.isBlank() ? fallbackTitle : title);
                                return encoder.encode(event);
                              });
                    }
                    return Flux.fromIterable(encoder.encode(event));
                  });
          return frames
              .concatWith(Mono.fromSupplier(encoder::done))
              .concatMap(
                  frame ->
                      Mono.fromCallable(
                              () -> {
                                if (sequence.compareAndSet(
                                    -1L, eventRepository.maxSequence(tenantId, runId.get()))) {
                                  // Initialized from durable replay state.
                                }
                                eventRepository.append(
                                    new PersistedAgentFrame(
                                        Ulid.next(),
                                        tenantId,
                                        runId.get(),
                                        sequence.incrementAndGet(),
                                        frame,
                                        Instant.now()));
                                return frame;
                              })
                          .subscribeOn(jdbcScheduler)
                          .map(String.class::cast));
        });
  }

  public Flux<String> replay(Identity identity, String idempotencyKey, long afterSequence) {
    return Mono.fromCallable(
            () ->
                runRepository
                    .findByIdempotencyKey(identity.tenantId(), identity.userId(), idempotencyKey)
                    .orElseThrow(
                        () ->
                            new io.github.ccweixiao.datastoria.common.error.NotFoundException(
                                "AgentRun", idempotencyKey)))
        .subscribeOn(jdbcScheduler)
        .flatMapMany(
            run ->
                Mono.fromCallable(
                        () ->
                            eventRepository.findAfter(identity.tenantId(), run.id(), afterSequence))
                    .subscribeOn(jdbcScheduler)
                    .flatMapIterable(list -> list)
                    .map(PersistedAgentFrame::frameText));
  }
}
