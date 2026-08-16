package io.github.ccweixiao.datastoria.agent.application;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.ccweixiao.datastoria.agent.runtime.AgentHarnessSettings;
import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.dto.AgentHarnessSettingsRequest;
import io.github.ccweixiao.datastoria.common.dto.AgentHarnessSettingsResponse;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ConfigEntryRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Resolves the effective agent harness settings: {@code datastoria.agent.*} process defaults merged
 * with the tenant-level {@code settings.ai.agent.harness} configuration entry that administrators
 * manage in the settings dialog. Only the four runtime knobs are overridable; the data directory
 * and shutdown timeout stay process-level.
 *
 * <p>Stored JSON shape (absent field = keep the process default): {@code {"maxIters":30,
 * "toolResultEvictionChars":16384,"compactionTriggerRatio":0.7,
 * "compactionFallbackContextTokens":200000}}. Values are re-clamped on every read, so a stale or
 * hand-edited entry cannot push a knob past its absolute bound.
 */
@Service
public class AgentHarnessSettingsService {

  static final String CONFIG_KEY = "settings.ai.agent.harness";

  private final AgentHarnessSettings base;
  private final ConfigEntryRepository configRepo;
  private final ObjectMapper mapper;
  private final Scheduler jdbcScheduler;

  public AgentHarnessSettingsService(
      AgentHarnessSettings base,
      ConfigEntryRepository configRepo,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.base = base;
    this.configRepo = configRepo;
    this.mapper = mapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  /** Process-level defaults from {@code datastoria.agent.*} (no tenant overrides applied). */
  public AgentHarnessSettings base() {
    return base;
  }

  /**
   * Tenant-effective settings. Blocking; callers run on the JDBC scheduler (run start and resume
   * preparation already do).
   */
  public AgentHarnessSettings effective(String tenantId) {
    return configRepo
        .findTenantEntry(tenantId, CONFIG_KEY)
        .map(
            entry -> {
              AgentHarnessSettingsRequest overrides = parseOverrides(entry.valueJson());
              return base.withRuntimeOverrides(
                  overrides.maxIters(),
                  overrides.toolResultEvictionChars(),
                  overrides.compactionTriggerRatio(),
                  overrides.compactionFallbackContextTokens());
            })
        .orElse(base);
  }

  /** Admin view: process defaults, stored overrides, and the effective merged values. */
  public Mono<AgentHarnessSettingsResponse> current(Identity identity) {
    return Mono.fromCallable(() -> report(identity.tenantId())).subscribeOn(jdbcScheduler);
  }

  public Mono<AgentHarnessSettingsResponse> update(
      AgentHarnessSettingsRequest req, Long ifMatch, Identity identity) {
    return Mono.fromCallable(
            () -> {
              ObjectNode value = mapper.createObjectNode();
              if (req.maxIters() != null) {
                value.put("maxIters", req.maxIters());
              }
              if (req.toolResultEvictionChars() != null) {
                value.put("toolResultEvictionChars", req.toolResultEvictionChars());
              }
              if (req.compactionTriggerRatio() != null) {
                value.put("compactionTriggerRatio", req.compactionTriggerRatio());
              }
              if (req.compactionFallbackContextTokens() != null) {
                value.put("compactionFallbackContextTokens", req.compactionFallbackContextTokens());
              }
              configRepo.upsertTenantEntry(
                  identity.tenantId(), CONFIG_KEY, value.toString(), ifMatch);
              return report(identity.tenantId());
            })
        .subscribeOn(jdbcScheduler);
  }

  private AgentHarnessSettingsResponse report(String tenantId) {
    var stored = configRepo.findTenantEntry(tenantId, CONFIG_KEY);
    AgentHarnessSettingsRequest overrides =
        stored
            .map(entry -> parseOverrides(entry.valueJson()))
            .orElse(AgentHarnessSettingsRequest.empty());
    AgentHarnessSettings effective =
        base.withRuntimeOverrides(
            overrides.maxIters(),
            overrides.toolResultEvictionChars(),
            overrides.compactionTriggerRatio(),
            overrides.compactionFallbackContextTokens());
    return new AgentHarnessSettingsResponse(
        knobs(base),
        overrides,
        knobs(effective),
        stored.map(io.github.ccweixiao.datastoria.common.domain.ConfigEntry::revision).orElse(0L));
  }

  private static AgentHarnessSettingsRequest knobs(AgentHarnessSettings settings) {
    return new AgentHarnessSettingsRequest(
        settings.maxIters(),
        settings.toolResultEvictionChars(),
        settings.compactionTriggerRatio(),
        settings.compactionFallbackContextTokens());
  }

  private AgentHarnessSettingsRequest parseOverrides(String valueJson) {
    try {
      JsonNode node = mapper.readTree(valueJson);
      return new AgentHarnessSettingsRequest(
          positiveIntOrNull(node.path("maxIters")),
          positiveIntOrNull(node.path("toolResultEvictionChars")),
          positiveDoubleOrNull(node.path("compactionTriggerRatio")),
          positiveIntOrNull(node.path("compactionFallbackContextTokens")));
    } catch (JsonProcessingException error) {
      // A malformed stored entry behaves like "no overrides"; admin can overwrite it.
      return AgentHarnessSettingsRequest.empty();
    }
  }

  private static Integer positiveIntOrNull(JsonNode node) {
    return node.canConvertToInt() && node.asInt() > 0 ? node.asInt() : null;
  }

  private static Double positiveDoubleOrNull(JsonNode node) {
    return node.isNumber() && node.asDouble() > 0 ? node.asDouble() : null;
  }
}
