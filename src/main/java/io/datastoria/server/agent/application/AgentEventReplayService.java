package io.datastoria.server.agent.application;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.datastoria.server.agent.domain.AgentRunEvent;
import io.datastoria.server.agent.domain.PersistedAgentFrame;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.AgentEventRepository;
import io.datastoria.server.repository.AgentRunRepository;

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
    return Flux.defer(
        () -> {
          AiSdkStreamEncoder encoder = new AiSdkStreamEncoder().withTitle(title);
          AtomicLong sequence = new AtomicLong();
          AtomicReference<String> runId = new AtomicReference<>();
          Flux<String> frames =
              events.concatMap(
                  event -> {
                    runId.compareAndSet(null, event.runId());
                    return Flux.fromIterable(encoder.encode(event));
                  });
          return frames
              .concatWith(Mono.fromSupplier(encoder::done))
              .concatMap(
                  frame ->
                      Mono.fromRunnable(
                              () ->
                                  eventRepository.append(
                                      new PersistedAgentFrame(
                                          Ulid.next(),
                                          tenantId,
                                          runId.get(),
                                          sequence.incrementAndGet(),
                                          frame,
                                          Instant.now())))
                          .subscribeOn(jdbcScheduler)
                          .thenReturn(frame));
        });
  }

  public Flux<String> replay(Identity identity, String idempotencyKey, long afterSequence) {
    return Mono.fromCallable(
            () ->
                runRepository
                    .findByIdempotencyKey(identity.tenantId(), identity.userId(), idempotencyKey)
                    .orElseThrow(
                        () ->
                            new io.datastoria.server.api.error.NotFoundException(
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
