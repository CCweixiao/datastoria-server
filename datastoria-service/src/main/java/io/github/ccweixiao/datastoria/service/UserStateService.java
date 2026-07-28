package io.github.ccweixiao.datastoria.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.UserState;
import io.github.ccweixiao.datastoria.common.dto.UserStateRequest;
import io.github.ccweixiao.datastoria.common.dto.UserStateResponse;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.UserStateRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class UserStateService {

  private final UserStateRepository repository;
  private final ObjectMapper mapper;
  private final Scheduler jdbcScheduler;

  public UserStateService(
      UserStateRepository repository,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.repository = repository;
    this.mapper = mapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<UserStateResponse>> list(String namespace, Identity identity) {
    return Mono.fromCallable(
            () ->
                repository.findAll(identity.tenantId(), identity.userId(), namespace).stream()
                    .map(this::response)
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<UserStateResponse> put(
      String namespace,
      String key,
      UserStateRequest request,
      Long expectedRevision,
      Identity identity) {
    return Mono.fromCallable(
            () ->
                response(
                    repository.upsert(
                        new UserState(
                            identity.tenantId(),
                            identity.userId(),
                            namespace,
                            key,
                            mapper.writeValueAsString(request.value()),
                            0,
                            null,
                            null),
                        expectedRevision)))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String namespace, String key, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> repository.delete(identity.tenantId(), identity.userId(), namespace, key))
        .subscribeOn(jdbcScheduler)
        .then();
  }

  private UserStateResponse response(UserState state) {
    try {
      return new UserStateResponse(
          state.key(),
          mapper.readTree(state.valueJson()),
          state.revision(),
          state.createdAt(),
          state.updatedAt());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Persisted user state is not valid JSON", e);
    }
  }
}
