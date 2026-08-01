package io.github.ccweixiao.datastoria.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.domain.Secret;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.ProviderResponse;
import io.github.ccweixiao.datastoria.common.dto.UserProviderRequest;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.ResourceInUseException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Private provider configurations and credentials isolated to one authenticated user. */
@Service
public class UserProviderService {

  private final ModelProviderRepository providers;
  private final ModelRepository models;
  private final SecretService secrets;
  private final Scheduler jdbcScheduler;

  public UserProviderService(
      ModelProviderRepository providers,
      ModelRepository models,
      SecretService secrets,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.providers = providers;
    this.models = models;
    this.secrets = secrets;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<ProviderResponse>> findAll(Identity identity) {
    return Mono.fromCallable(
            () ->
                providers.findUserProviders(identity.tenantId(), identity.userId()).stream()
                    .map(this::response)
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ProviderResponse> create(UserProviderRequest request, Identity identity) {
    return Mono.fromCallable(() -> createNow(request, identity)).subscribeOn(jdbcScheduler);
  }

  public Mono<ProviderResponse> update(
      String id, Long expectedRevision, UserProviderRequest request, Identity identity) {
    return Mono.fromCallable(() -> updateNow(id, expectedRevision, request, identity))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String id, Long expectedRevision, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              ModelProvider existing = requireOwned(id, identity);
              if (models.existsByProviderId(id, identity.tenantId())) {
                throw new ResourceInUseException("Provider", id);
              }
              providers.softDelete(
                  id,
                  identity.tenantId(),
                  expectedRevision != null ? expectedRevision : existing.revision());
              if (existing.secretId() != null) {
                secrets.delete(existing.secretId(), identity.tenantId());
              }
            })
        .subscribeOn(jdbcScheduler)
        .then();
  }

  private ProviderResponse createNow(UserProviderRequest request, Identity identity) {
    Secret secret = saveCredential(request.apiKey(), identity);
    ModelProvider provider =
        new ModelProvider(
            Ulid.next(),
            identity.tenantId(),
            identity.userId(),
            request.providerKey().trim(),
            request.displayName().trim(),
            trimUrl(request.baseUrl()),
            "api_key",
            true,
            "{}",
            secret.id(),
            0,
            identity.userId(),
            identity.userId(),
            null,
            null,
            null);
    try {
      return ProviderResponse.from(providers.save(provider), secret);
    } catch (RuntimeException ex) {
      secrets.delete(secret.id(), identity.tenantId());
      throw ex;
    }
  }

  private ProviderResponse updateNow(
      String id, Long expectedRevision, UserProviderRequest request, Identity identity) {
    ModelProvider existing = requireOwned(id, identity);
    Secret replacement = null;
    String secretId = existing.secretId();
    if (request.apiKey() != null && !request.apiKey().isBlank()) {
      replacement = saveCredential(request.apiKey(), identity);
      secretId = replacement.id();
    }
    ModelProvider updated =
        new ModelProvider(
            existing.id(),
            existing.tenantId(),
            existing.ownerUserId(),
            request.providerKey().trim(),
            request.displayName().trim(),
            trimUrl(request.baseUrl()),
            existing.authType(),
            existing.enabled(),
            existing.configJson(),
            secretId,
            existing.revision(),
            existing.createdBy(),
            identity.userId(),
            existing.createdAt(),
            null,
            null);
    try {
      ModelProvider saved =
          providers.update(
              updated, expectedRevision != null ? expectedRevision : existing.revision());
      if (replacement != null) {
        if (existing.secretId() != null) {
          secrets.delete(existing.secretId(), identity.tenantId());
        }
      }
      return response(saved);
    } catch (RuntimeException ex) {
      if (replacement != null) {
        secrets.delete(replacement.id(), identity.tenantId());
      }
      throw ex;
    }
  }

  private ModelProvider requireOwned(String id, Identity identity) {
    return providers
        .findUserById(id, identity.tenantId(), identity.userId())
        .orElseThrow(() -> new NotFoundException("Provider", id));
  }

  private Secret saveCredential(String apiKey, Identity identity) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key is required");
    }
    return secrets.save(identity.tenantId(), identity.userId(), "api_key", apiKey.trim(), null);
  }

  private ProviderResponse response(ModelProvider provider) {
    Secret secret =
        provider.secretId() == null
            ? null
            : secrets.findMaskedById(provider.secretId(), provider.tenantId()).orElse(null);
    return ProviderResponse.from(provider, secret);
  }

  private static String trimUrl(String value) {
    return value.trim().replaceAll("/+$", "");
  }
}
