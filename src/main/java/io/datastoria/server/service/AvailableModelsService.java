package io.datastoria.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.dto.AvailableModelsResponse;
import io.datastoria.server.dto.ModelProps;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.repository.ModelRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Reads enabled models from the catalog and maps them to the A12 {@link ModelProps} shape expected
 * by the frontend.
 */
@Service
public class AvailableModelsService {

  private final ModelRepository modelRepo;
  private final ModelProviderRepository providerRepo;
  private final Scheduler jdbcScheduler;

  public AvailableModelsService(
      ModelRepository modelRepo,
      ModelProviderRepository providerRepo,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.modelRepo = modelRepo;
    this.providerRepo = providerRepo;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<AvailableModelsResponse> getAvailableModels(Identity identity) {
    return Mono.fromCallable(() -> AvailableModelsResponse.systemOnly(buildSystemModels(identity)))
        .subscribeOn(jdbcScheduler);
  }

  private List<ModelProps> buildSystemModels(Identity identity) {
    List<ModelProvider> providers = providerRepo.findAll(identity.tenantId());
    return modelRepo.findEnabled(identity.tenantId()).stream()
        .map(m -> toModelProps(m, providers))
        .toList();
  }

  private ModelProps toModelProps(Model model, List<ModelProvider> providers) {
    String providerKey =
        providers.stream()
            .filter(p -> p.id().equals(model.providerId()))
            .map(ModelProvider::providerKey)
            .findFirst()
            .orElse("unknown");
    return new ModelProps(
        providerKey,
        model.modelKey(),
        model.description(),
        model.isFree(),
        true,
        !model.enabled(),
        List.of("chat"),
        false,
        true,
        false,
        List.of(),
        model.source(),
        model.id());
  }
}
