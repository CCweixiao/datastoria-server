package io.github.ccweixiao.datastoria.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.agent.runtime.AgentHarnessSettings;
import io.github.ccweixiao.datastoria.common.domain.ConfigEntry;
import io.github.ccweixiao.datastoria.common.dto.AgentHarnessSettingsRequest;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ConfigEntryRepository;

import reactor.core.scheduler.Schedulers;

class AgentHarnessSettingsServiceTest {

  private final ConfigEntryRepository configRepo = mock(ConfigEntryRepository.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private AgentHarnessSettingsService service;

  @BeforeEach
  void setUp() {
    service =
        new AgentHarnessSettingsService(
            new AgentHarnessSettings(null, 25, 32_768, 0.8, 100_000, 20),
            configRepo,
            mapper,
            Schedulers.immediate());
  }

  @Test
  void effectiveFallsBackToProcessDefaultsWithoutATenantEntry() {
    when(configRepo.findTenantEntry("tenant", AgentHarnessSettingsService.CONFIG_KEY))
        .thenReturn(Optional.empty());

    AgentHarnessSettings effective = service.effective("tenant");

    assertThat(effective.maxIters()).isEqualTo(25);
    assertThat(effective.toolResultEvictionChars()).isEqualTo(32_768);
  }

  @Test
  void tenantOverridesReplaceProvidedKnobsAndAreClampedOnRead() {
    when(configRepo.findTenantEntry("tenant", AgentHarnessSettingsService.CONFIG_KEY))
        .thenReturn(Optional.of(entry("{\"maxIters\":40,\"compactionTriggerRatio\":0.5}", 3)));

    AgentHarnessSettings effective = service.effective("tenant");

    assertThat(effective.maxIters()).isEqualTo(40);
    assertThat(effective.compactionTriggerRatio()).isEqualTo(0.5);
    // Not overridden:
    assertThat(effective.toolResultEvictionChars()).isEqualTo(32_768);
    assertThat(effective.compactionFallbackContextTokens()).isEqualTo(100_000);
  }

  @Test
  void malformedStoredEntryBehavesLikeNoOverrides() {
    when(configRepo.findTenantEntry("tenant", AgentHarnessSettingsService.CONFIG_KEY))
        .thenReturn(Optional.of(entry("{not json", 1)));

    assertThat(service.effective("tenant").maxIters()).isEqualTo(25);
  }

  @Test
  void updatePersistsOnlyProvidedKnobsAsTenantEntry() {
    // upsertTenantEntry is mocked; the only findTenantEntry call is report()'s post-write read.
    when(configRepo.findTenantEntry(anyString(), anyString()))
        .thenReturn(Optional.of(entry("{\"maxIters\":30,\"compactionTriggerRatio\":0.7}", 1)));
    Identity admin = new Identity("tenant", "admin", java.util.Set.of("ROLE_ADMIN"));

    var response =
        service.update(new AgentHarnessSettingsRequest(30, null, 0.7, null), null, admin).block();

    ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
    verify(configRepo)
        .upsertTenantEntry(
            eq("tenant"), eq(AgentHarnessSettingsService.CONFIG_KEY), value.capture(), any());
    assertThat(value.getValue())
        .contains("\"maxIters\":30")
        .contains("\"compactionTriggerRatio\":0.7");
    assertThat(value.getValue()).doesNotContain("toolResultEvictionChars");
    assertThat(response.overrides().maxIters()).isEqualTo(30);
    assertThat(response.effective().compactionTriggerRatio()).isEqualTo(0.7);
    assertThat(response.defaults().maxIters()).isEqualTo(25);
  }

  private static ConfigEntry entry(String valueJson, long revision) {
    return new ConfigEntry(
        "id",
        "tenant",
        "tenant",
        "tenant",
        AgentHarnessSettingsService.CONFIG_KEY,
        valueJson,
        "1",
        revision,
        null,
        null,
        null);
  }
}
