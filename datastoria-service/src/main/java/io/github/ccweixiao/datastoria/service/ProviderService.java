package io.github.ccweixiao.datastoria.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.domain.Secret;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.CredentialRequest;
import io.github.ccweixiao.datastoria.common.dto.CredentialResponse;
import io.github.ccweixiao.datastoria.common.dto.DiscoveredModelResponse;
import io.github.ccweixiao.datastoria.common.dto.ProviderResponse;
import io.github.ccweixiao.datastoria.common.dto.ProviderTestResponse;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.ResourceInUseException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderSecretRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Application service for model provider CRUD and credential rotation. All JDBC calls are offloaded
 * to the {@code jdbcScheduler} so the Netty event loop is never blocked.
 */
@Service
public class ProviderService {

  private static final Logger log = LoggerFactory.getLogger(ProviderService.class);

  private final ModelProviderRepository providerRepo;
  private final ModelProviderSecretRepository providerSecrets;
  private final ModelRepository modelRepo;
  private final SecretService secretService;
  private final TransactionTemplate transactions;
  private final ProviderRemoteClient remoteClient;
  private final ModelCatalogEnricher catalogEnricher;
  private final Scheduler jdbcScheduler;

  public ProviderService(
      ModelProviderRepository providerRepo,
      ModelProviderSecretRepository providerSecrets,
      ModelRepository modelRepo,
      SecretService secretService,
      TransactionTemplate transactions,
      ProviderRemoteClient remoteClient,
      ModelCatalogEnricher catalogEnricher,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.providerRepo = providerRepo;
    this.providerSecrets = providerSecrets;
    this.modelRepo = modelRepo;
    this.secretService = secretService;
    this.transactions = transactions;
    this.remoteClient = remoteClient;
    this.catalogEnricher = catalogEnricher;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<ProviderResponse>> findAll(Identity identity) {
    return Mono.fromCallable(
            () ->
                providerRepo.findSystemProviders(identity.tenantId()).stream()
                    .map(
                        p -> {
                          Secret s =
                              p.secretId() == null
                                  ? null
                                  : secretService
                                      .findMaskedById(p.secretId(), identity.tenantId())
                                      .orElse(null);
                          return ProviderResponse.from(p, s);
                        })
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ProviderResponse> findById(String id, Identity identity) {
    return Mono.fromCallable(
            () -> {
              ModelProvider p =
                  providerRepo
                      .findSystemById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Provider", id));
              Secret s =
                  p.secretId() == null
                      ? null
                      : secretService
                          .findMaskedById(p.secretId(), identity.tenantId())
                          .orElse(null);
              return ProviderResponse.from(p, s);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ProviderResponse> create(
      io.github.ccweixiao.datastoria.common.dto.CreateProviderRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              ModelProvider p =
                  new ModelProvider(
                      Ulid.next(),
                      identity.tenantId(),
                      null,
                      req.providerKey(),
                      req.displayName(),
                      req.baseUrl(),
                      req.authType(),
                      req.enabled() == null || req.enabled(),
                      req.configJson() != null ? req.configJson() : "{}",
                      null,
                      0,
                      identity.userId(),
                      identity.userId(),
                      null,
                      null,
                      null);
              return ProviderResponse.from(providerRepo.save(p));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ProviderResponse> update(
      String id,
      Long ifMatch,
      io.github.ccweixiao.datastoria.common.dto.UpdateProviderRequest req,
      Identity identity) {
    return Mono.fromCallable(
            () -> {
              ModelProvider existing =
                  providerRepo
                      .findSystemById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Provider", id));
              long expected = ifMatch != null ? ifMatch : existing.revision();
              ModelProvider updated =
                  new ModelProvider(
                      existing.id(),
                      existing.tenantId(),
                      null,
                      existing.providerKey(),
                      req.displayName(),
                      req.baseUrl(),
                      req.authType(),
                      req.enabled() != null ? req.enabled() : existing.enabled(),
                      req.configJson() != null ? req.configJson() : existing.configJson(),
                      existing.secretId(),
                      existing.revision(),
                      identity.userId(),
                      identity.userId(),
                      existing.createdAt(),
                      null,
                      null);
              ModelProvider saved = providerRepo.update(updated, expected);
              Secret s =
                  saved.secretId() == null
                      ? null
                      : secretService
                          .findMaskedById(saved.secretId(), identity.tenantId())
                          .orElse(null);
              return ProviderResponse.from(saved, s);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String id, Long ifMatch, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              ModelProvider existing =
                  providerRepo
                      .findSystemById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Provider", id));
              if (modelRepo.existsByProviderId(id, identity.tenantId())) {
                throw new ResourceInUseException("Provider", id);
              }
              providerRepo.softDelete(
                  id, identity.tenantId(), ifMatch != null ? ifMatch : existing.revision());
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<CredentialResponse> putCredential(
      String providerId, CredentialRequest req, Identity identity) {
    return Mono.fromCallable(
            () ->
                transactions.execute(
                    status -> {
                      ModelProvider provider =
                          providerRepo
                              .findSystemById(providerId, identity.tenantId())
                              .orElseThrow(() -> new NotFoundException("Provider", providerId));
                      Secret saved =
                          secretService.save(
                              identity.tenantId(),
                              null,
                              req.secretKind(),
                              req.value(),
                              req.expiresAt());
                      providerSecrets.updateSecretId(providerId, identity.tenantId(), saved.id());
                      if (provider.secretId() != null) {
                        secretService.delete(provider.secretId(), identity.tenantId());
                      }
                      return new CredentialResponse(true, saved.maskedHint(), saved.updatedAt());
                    }))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> deleteCredential(String providerId, Identity identity) {
    return Mono.<Void>fromRunnable(
            () ->
                transactions.executeWithoutResult(
                    status -> {
                      ModelProvider provider =
                          providerRepo
                              .findSystemById(providerId, identity.tenantId())
                              .orElseThrow(() -> new NotFoundException("Provider", providerId));
                      if (provider.secretId() != null) {
                        providerSecrets.updateSecretId(providerId, identity.tenantId(), null);
                        secretService.delete(provider.secretId(), identity.tenantId());
                      }
                    }))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ProviderTestResponse> testConnection(String providerId, Identity identity) {
    return Mono.fromCallable(
            () -> {
              long started = System.nanoTime();
              discover(providerId, identity);
              long latencyMs =
                  java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
              return new ProviderTestResponse(true, latencyMs, "Connection succeeded");
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<List<DiscoveredModelResponse>> discoverModels(String providerId, Identity identity) {
    return Mono.fromCallable(() -> discover(providerId, identity)).subscribeOn(jdbcScheduler);
  }

  private List<DiscoveredModelResponse> discover(String providerId, Identity identity) {
    ModelProvider provider =
        providerRepo
            .findSystemById(providerId, identity.tenantId())
            .orElseThrow(() -> new NotFoundException("Provider", providerId));
    if (provider.secretId() == null) {
      throw new io.github.ccweixiao.datastoria.common.error.ProviderOperationException(
          "PROVIDER_CREDENTIAL_MISSING", 409, "Provider has no configured credential");
    }
    String credential = secretService.decrypt(provider.secretId(), identity.tenantId());
    try {
      List<DiscoveredModelResponse> discovered = remoteClient.discoverModels(provider, credential);
      List<DiscoveredModelResponse> enriched =
          catalogEnricher.enrich(provider.providerKey(), discovered);
      log.info(
          "Model sync for provider '{}' returned {} models (catalog metadata applied to {})",
          provider.providerKey(),
          discovered.size(),
          enriched.stream()
              .filter(m -> m.contextWindowTokens() != null || Boolean.TRUE.equals(m.isFree()))
              .count());
      for (DiscoveredModelResponse model : enriched) {
        log.info(
            "Model sync '{}': {} ctx={} out={} reasoning={} image={} free={} tier={}",
            provider.providerKey(),
            model.modelKey(),
            model.contextWindowTokens() == null ? "-" : model.contextWindowTokens(),
            model.maxOutputTokens() == null ? "-" : model.maxOutputTokens(),
            model.supportsReasoning(),
            model.supportsImageInput(),
            model.isFree(),
            model.tier());
      }
      return enriched;
    } catch (RuntimeException error) {
      log.warn("Model sync for provider '{}' failed: {}", provider.providerKey(), error.toString());
      throw error;
    } finally {
      // Strings cannot be zeroed; keep scope minimal and never log or retain the value.
      credential = null;
    }
  }
}
