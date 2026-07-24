package io.datastoria.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.dto.ModelResponse;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.repository.ModelRepository;

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
            () -> modelRepo.findAll(identity.tenantId()).stream().map(ModelResponse::from).toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ModelResponse> findById(String id, Identity identity) {
    return Mono.fromCallable(
            () ->
                ModelResponse.from(
                    modelRepo
                        .findById(id, identity.tenantId())
                        .orElseThrow(() -> new NotFoundException("Model", id))))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ModelResponse> create(
      io.datastoria.server.dto.CreateModelRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              providerRepo
                  .findById(req.providerId(), identity.tenantId())
                  .orElseThrow(() -> new NotFoundException("Provider", req.providerId()));
              Model m =
                  new Model(
                      Ulid.next(),
                      identity.tenantId(),
                      req.providerId(),
                      req.modelKey(),
                      req.displayName(),
                      req.description(),
                      req.source(),
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
      String id, Long ifMatch, io.datastoria.server.dto.UpdateModelRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              Model existing =
                  modelRepo
                      .findById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Model", id));
              long expected = ifMatch != null ? ifMatch : existing.revision();
              Model updated =
                  new Model(
                      existing.id(),
                      existing.tenantId(),
                      existing.providerId(),
                      existing.modelKey(),
                      req.displayName(),
                      req.description(),
                      req.source(),
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
                      .findById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Model", id));
              long expected = ifMatch != null ? ifMatch : existing.revision();
              modelRepo.softDelete(id, identity.tenantId(), expected);
            })
        .subscribeOn(jdbcScheduler);
  }
}
