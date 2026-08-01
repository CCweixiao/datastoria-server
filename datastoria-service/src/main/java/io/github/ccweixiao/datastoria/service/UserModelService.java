package io.github.ccweixiao.datastoria.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.domain.Secret;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.UserModelRequest;
import io.github.ccweixiao.datastoria.common.dto.UserModelResponse;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** CRUD for models owned by, and visible only to, the authenticated user. */
@Service
public class UserModelService {

  private final ModelRepository models;
  private final ModelProviderRepository providers;
  private final SecretService secrets;
  private final Scheduler jdbcScheduler;

  public UserModelService(
      ModelRepository models,
      ModelProviderRepository providers,
      SecretService secrets,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.models = models;
    this.providers = providers;
    this.secrets = secrets;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<UserModelResponse>> findAll(Identity identity) {
    return Mono.fromCallable(
            () ->
                models.findUserModels(identity.tenantId(), identity.userId()).stream()
                    .map(this::response)
                    .toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<UserModelResponse> create(UserModelRequest request, Identity identity) {
    return Mono.fromCallable(() -> createNow(request, identity)).subscribeOn(jdbcScheduler);
  }

  public Mono<UserModelResponse> update(
      String id, Long expectedRevision, UserModelRequest request, Identity identity) {
    return Mono.fromCallable(() -> updateNow(id, expectedRevision, request, identity))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String id, Long expectedRevision, Identity identity) {
    return Mono.<Void>fromRunnable(
            () -> {
              Model existing = requireOwned(id, identity);
              models.softDelete(
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

  private UserModelResponse createNow(UserModelRequest request, Identity identity) {
    var provider = requireProvider(request.providerId(), identity);
    Secret secret =
        provider.ownerUserId() == null ? saveCredential(request.apiKey(), identity) : null;
    Model model =
        new Model(
            Ulid.next(),
            identity.tenantId(),
            identity.userId(),
            request.providerId(),
            request.modelKey().trim(),
            request.displayName().trim(),
            trimToNull(request.description()),
            "custom",
            true,
            false,
            "{}",
            "{}",
            secret != null ? secret.id() : null,
            0,
            null,
            null,
            null);
    try {
      return UserModelResponse.from(
          models.save(model), secret != null ? secret.maskedHint() : null);
    } catch (RuntimeException ex) {
      if (secret != null) {
        secrets.delete(secret.id(), identity.tenantId());
      }
      throw ex;
    }
  }

  private UserModelResponse updateNow(
      String id, Long expectedRevision, UserModelRequest request, Identity identity) {
    Model existing = requireOwned(id, identity);
    ModelProvider provider = requireProvider(request.providerId(), identity);
    Secret replacement = null;
    String secretId = existing.secretId();
    if (provider.ownerUserId() == null && request.apiKey() != null && !request.apiKey().isBlank()) {
      replacement = saveCredential(request.apiKey(), identity);
      secretId = replacement.id();
    }
    Model updated =
        new Model(
            existing.id(),
            existing.tenantId(),
            existing.ownerUserId(),
            request.providerId(),
            request.modelKey().trim(),
            request.displayName().trim(),
            trimToNull(request.description()),
            "custom",
            existing.enabled(),
            existing.isFree(),
            existing.capabilitiesJson(),
            existing.generationDefaultsJson(),
            secretId,
            existing.revision(),
            existing.createdAt(),
            null,
            null);
    try {
      Model saved =
          models.update(updated, expectedRevision != null ? expectedRevision : existing.revision());
      if (replacement != null && existing.secretId() != null) {
        secrets.delete(existing.secretId(), identity.tenantId());
      }
      return response(saved);
    } catch (RuntimeException ex) {
      if (replacement != null) {
        secrets.delete(replacement.id(), identity.tenantId());
      }
      throw ex;
    }
  }

  private Model requireOwned(String id, Identity identity) {
    return models
        .findUserById(id, identity.tenantId(), identity.userId())
        .orElseThrow(() -> new NotFoundException("Model", id));
  }

  private ModelProvider requireProvider(String providerId, Identity identity) {
    return providers.findAccessibleProviders(identity.tenantId(), identity.userId()).stream()
        .filter(provider -> provider.id().equals(providerId))
        .filter(provider -> provider.enabled() && !"oauth".equalsIgnoreCase(provider.authType()))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Provider", providerId));
  }

  private Secret saveCredential(String apiKey, Identity identity) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("API key is required");
    }
    return secrets.save(identity.tenantId(), identity.userId(), "api_key", apiKey.trim(), null);
  }

  private UserModelResponse response(Model model) {
    String hint =
        model.secretId() == null
            ? null
            : secrets
                .findMaskedById(model.secretId(), model.tenantId())
                .map(Secret::maskedHint)
                .orElse(null);
    return UserModelResponse.from(model, hint);
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
