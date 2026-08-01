package io.github.ccweixiao.datastoria.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.ModelResponse;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Application service for model catalog CRUD. */
@Service
public class ModelService {

  private final ModelRepository modelRepo;
  private final ModelProviderRepository providerRepo;
  private final Scheduler jdbcScheduler;

  public ModelService(
      ModelRepository modelRepo,
      ModelProviderRepository providerRepo,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.modelRepo = modelRepo;
    this.providerRepo = providerRepo;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<ModelResponse>> findAll(Identity identity) {
    return Mono.fromCallable(
            () ->
                modelRepo.findSystemModels(identity.tenantId()).stream()
                    .map(ModelResponse::from)
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ModelResponse> findById(String id, Identity identity) {
    return Mono.fromCallable(
            () ->
                ModelResponse.from(
                    modelRepo
                        .findSystemById(id, identity.tenantId())
                        .orElseThrow(() -> new NotFoundException("Model", id))))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ModelResponse> create(
      io.github.ccweixiao.datastoria.common.dto.CreateModelRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              providerRepo
                  .findById(req.providerId(), identity.tenantId())
                  .orElseThrow(() -> new NotFoundException("Provider", req.providerId()));
              Model m =
                  new Model(
                      Ulid.next(),
                      identity.tenantId(),
                      null,
                      req.providerId(),
                      req.modelKey(),
                      req.displayName(),
                      req.description(),
                      "system",
                      req.enabled() == null || req.enabled(),
                      req.isFree() != null && req.isFree(),
                      req.capabilitiesJson(),
                      req.generationDefaultsJson(),
                      null,
                      0,
                      null,
                      null,
                      null);
              return ModelResponse.from(modelRepo.save(m));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ModelResponse> update(
      String id,
      Long ifMatch,
      io.github.ccweixiao.datastoria.common.dto.UpdateModelRequest req,
      Identity identity) {
    return Mono.fromCallable(
            () -> {
              Model existing =
                  modelRepo
                      .findSystemById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Model", id));
              long expected = ifMatch != null ? ifMatch : existing.revision();
              Model updated =
                  new Model(
                      existing.id(),
                      existing.tenantId(),
                      existing.ownerUserId(),
                      existing.providerId(),
                      existing.modelKey(),
                      req.displayName(),
                      req.description(),
                      "system",
                      req.enabled() != null ? req.enabled() : existing.enabled(),
                      req.isFree() != null ? req.isFree() : existing.isFree(),
                      req.capabilitiesJson() != null
                          ? req.capabilitiesJson()
                          : existing.capabilitiesJson(),
                      req.generationDefaultsJson() != null
                          ? req.generationDefaultsJson()
                          : existing.generationDefaultsJson(),
                      existing.secretId(),
                      existing.revision(),
                      existing.createdAt(),
                      null,
                      null);
              return ModelResponse.from(modelRepo.update(updated, expected));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String id, Long ifMatch, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              Model existing =
                  modelRepo
                      .findSystemById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Model", id));
              long expected = ifMatch != null ? ifMatch : existing.revision();
              modelRepo.softDelete(id, identity.tenantId(), expected);
            })
        .subscribeOn(jdbcScheduler);
  }
}
