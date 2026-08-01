package io.github.ccweixiao.datastoria.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.CkQueryHistory;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.CkQueryHistoryRequest;
import io.github.ccweixiao.datastoria.common.dto.CkQueryHistoryResponse;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.CkQueryHistoryRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * ClickHouse query history. The repository owns dedup-on-rerun and the per-(user, connection) cap;
 * this service maps the {@link Identity} (tenantId, userId) onto every call. Listing is always
 * scoped by connectionId first (the first-level filter), then ordered time-desc, then optionally
 * filtered by keyword on the raw SQL.
 */
@Service
public class CkQueryHistoryService {

  private final CkQueryHistoryRepository repository;
  private final Scheduler jdbcScheduler;

  public CkQueryHistoryService(
      CkQueryHistoryRepository repository,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.repository = repository;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<CkQueryHistoryResponse>> list(
      String connectionId, String keyword, Identity identity) {
    String normalizedKeyword = normalizeKeyword(keyword);
    return Mono.fromCallable(
            () ->
                repository
                    .find(identity.tenantId(), identity.userId(), connectionId, normalizedKeyword)
                    .stream()
                    .map(CkQueryHistoryResponse::from)
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<CkQueryHistoryResponse> add(CkQueryHistoryRequest request, Identity identity) {
    Instant now = Instant.now();
    CkQueryHistory entry =
        new CkQueryHistory(
            Ulid.next(),
            identity.tenantId(),
            identity.userId(),
            request.connectionId(),
            request.connectionName(),
            request.rawSql(),
            now,
            now);
    return Mono.fromCallable(() -> CkQueryHistoryResponse.from(repository.save(entry)))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String id, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> repository.delete(id, identity.tenantId(), identity.userId()))
        .subscribeOn(jdbcScheduler)
        .then();
  }

  public Mono<Void> clear(String connectionId, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> repository.clear(identity.tenantId(), identity.userId(), connectionId))
        .subscribeOn(jdbcScheduler)
        .then();
  }

  private static String normalizeKeyword(String keyword) {
    if (keyword == null) {
      return null;
    }
    String trimmed = keyword.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
