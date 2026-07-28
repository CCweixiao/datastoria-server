package io.github.ccweixiao.datastoria.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.ConfigEntry;
import io.github.ccweixiao.datastoria.common.domain.EffectiveConfig;
import io.github.ccweixiao.datastoria.common.dto.UpdateUserPreferenceRequest;
import io.github.ccweixiao.datastoria.common.dto.UserModelPreferenceRequest;
import io.github.ccweixiao.datastoria.common.dto.UserModelPreferenceResponse;
import io.github.ccweixiao.datastoria.common.dto.UserPreferenceResponse;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ConfigEntryRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;
import io.github.ccweixiao.datastoria.dao.repository.UserModelPreferenceRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Merges system &lt; tenant &lt; user configuration layers and manages per-user model selection.
 * User-scope entries always win over tenant, which wins over system; within the same scope, the
 * latest revision wins.
 */
@Service
public class UserPreferenceService {

  private static final int SYSTEM = 0;
  private static final int TENANT = 1;
  private static final int USER = 2;

  private final ConfigEntryRepository configRepo;
  private final UserModelPreferenceRepository modelPrefRepo;
  private final ModelRepository modelRepo;
  private final SystemConfigurationProvisioner configurationProvisioner;
  private final Scheduler jdbcScheduler;

  public UserPreferenceService(
      ConfigEntryRepository configRepo,
      UserModelPreferenceRepository modelPrefRepo,
      ModelRepository modelRepo,
      SystemConfigurationProvisioner configurationProvisioner,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.configRepo = configRepo;
    this.modelPrefRepo = modelPrefRepo;
    this.modelRepo = modelRepo;
    this.configurationProvisioner = configurationProvisioner;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<UserPreferenceResponse> getEffectiveConfig(Identity identity) {
    return Mono.fromCallable(() -> UserPreferenceResponse.from(merge(identity)))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<UserPreferenceResponse> upsertUserEntry(
      UpdateUserPreferenceRequest req, Long ifMatch, Identity identity) {
    return Mono.fromCallable(
            () -> {
              configRepo.upsertUserEntry(
                  identity.tenantId(),
                  identity.userId(),
                  req.configKey(),
                  req.valueJson(),
                  ifMatch);
              return UserPreferenceResponse.from(merge(identity));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<UserModelPreferenceResponse> getModelPreference(Identity identity) {
    return Mono.fromCallable(
            () ->
                modelPrefRepo
                    .findByUser(identity.tenantId(), identity.userId())
                    .map(UserModelPreferenceResponse::from)
                    .orElseGet(() -> new UserModelPreferenceResponse(null, null, 0)))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<UserModelPreferenceResponse> setModelPreference(
      UserModelPreferenceRequest req, Long ifMatch, Identity identity) {
    return Mono.fromCallable(
            () -> {
              modelRepo
                  .findById(req.modelConfigId(), identity.tenantId())
                  .orElseThrow(
                      () ->
                          new io.github.ccweixiao.datastoria.common.error.NotFoundException(
                              "Model", req.modelConfigId()));
              return UserModelPreferenceResponse.from(
                  modelPrefRepo.upsert(
                      identity.tenantId(),
                      identity.userId(),
                      req.modelConfigId(),
                      req.preferenceJson(),
                      ifMatch));
            })
        .subscribeOn(jdbcScheduler);
  }

  private EffectiveConfig merge(Identity identity) {
    configurationProvisioner.provision(identity.tenantId());
    List<ConfigEntry> entries = configRepo.findEffective(identity.tenantId(), identity.userId());
    List<ConfigEntry> sorted =
        entries.stream()
            .sorted(
                Comparator.comparingInt(UserPreferenceService::scopeRank)
                    .thenComparingLong(ConfigEntry::revision))
            .toList();
    Map<String, String> merged = new LinkedHashMap<>();
    for (ConfigEntry e : sorted) {
      merged.put(e.configKey(), e.valueJson());
    }
    long maxRevision = sorted.stream().mapToLong(ConfigEntry::revision).max().orElse(0);
    return new EffectiveConfig(merged, maxRevision);
  }

  private static int scopeRank(ConfigEntry entry) {
    return switch (entry.scopeType()) {
      case "system" -> SYSTEM;
      case "tenant" -> TENANT;
      case "user" -> USER;
      default -> SYSTEM;
    };
  }
}
