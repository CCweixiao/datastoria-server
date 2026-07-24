package io.datastoria.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.ResourceInUseException;
import io.datastoria.server.config.JdbcSchedulerConfig;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.domain.Secret;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.dto.CredentialRequest;
import io.datastoria.server.dto.CredentialResponse;
import io.datastoria.server.dto.DiscoveredModelResponse;
import io.datastoria.server.dto.ProviderResponse;
import io.datastoria.server.dto.ProviderTestResponse;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.repository.ModelRepository;
import io.datastoria.server.repository.jdbc.JdbcModelProviderRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Application service for model provider CRUD and credential rotation. All JDBC calls are offloaded
 * to the {@code jdbcScheduler} so the Netty event loop is never blocked.
 */
@Service
public class ProviderService {

  private final ModelProviderRepository providerRepo;
  private final JdbcModelProviderRepository jdbcProviderRepo;
  private final ModelRepository modelRepo;
  private final SecretService secretService;
  private final TransactionTemplate transactions;
  private final ProviderRemoteClient remoteClient;
  private final Scheduler jdbcScheduler;

  public ProviderService(
      ModelProviderRepository providerRepo,
      JdbcModelProviderRepository jdbcProviderRepo,
      ModelRepository modelRepo,
      SecretService secretService,
      TransactionTemplate transactions,
      ProviderRemoteClient remoteClient,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.providerRepo = providerRepo;
    this.jdbcProviderRepo = jdbcProviderRepo;
    this.modelRepo = modelRepo;
    this.secretService = secretService;
    this.transactions = transactions;
    this.remoteClient = remoteClient;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<ProviderResponse>> findAll(Identity identity) {
    return Mono.fromCallable(
            () ->
                providerRepo.findAll(identity.tenantId()).stream()
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
                      .findById(id, identity.tenantId())
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
      io.datastoria.server.dto.CreateProviderRequest req, Identity identity) {
    return Mono.fromCallable(
            () -> {
              ModelProvider p =
                  new ModelProvider(
                      Ulid.next(),
                      identity.tenantId(),
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
      io.datastoria.server.dto.UpdateProviderRequest req,
      Identity identity) {
    return Mono.fromCallable(
            () -> {
              ModelProvider existing =
                  providerRepo
                      .findById(id, identity.tenantId())
                      .orElseThrow(() -> new NotFoundException("Provider", id));
              long expected = ifMatch != null ? ifMatch : existing.revision();
              ModelProvider updated =
                  new ModelProvider(
                      existing.id(),
                      existing.tenantId(),
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
                      .findById(id, identity.tenantId())
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
                              .findById(providerId, identity.tenantId())
                              .orElseThrow(() -> new NotFoundException("Provider", providerId));
                      Secret saved =
                          secretService.save(
                              identity.tenantId(),
                              null,
                              req.secretKind(),
                              req.value(),
                              req.expiresAt());
                      jdbcProviderRepo.updateSecretId(providerId, identity.tenantId(), saved.id());
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
                              .findById(providerId, identity.tenantId())
                              .orElseThrow(() -> new NotFoundException("Provider", providerId));
                      if (provider.secretId() != null) {
                        jdbcProviderRepo.updateSecretId(providerId, identity.tenantId(), null);
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
            .findById(providerId, identity.tenantId())
            .orElseThrow(() -> new NotFoundException("Provider", providerId));
    if (provider.secretId() == null) {
      throw new io.datastoria.server.api.error.ProviderOperationException(
          "PROVIDER_CREDENTIAL_MISSING", 409, "Provider has no configured credential");
    }
    String credential = secretService.decrypt(provider.secretId(), identity.tenantId());
    try {
      return remoteClient.discoverModels(provider, credential);
    } finally {
      // Strings cannot be zeroed; keep scope minimal and never log or retain the value.
      credential = null;
    }
  }
}
