package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;
import io.github.ccweixiao.datastoria.common.domain.ConfigEntry;
import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.dto.UpdateUserPreferenceRequest;
import io.github.ccweixiao.datastoria.common.dto.UserModelPreferenceRequest;
import io.github.ccweixiao.datastoria.common.dto.UserPreferenceResponse;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ConfigEntryRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

@SpringBootTest
@ActiveProfiles("dev")
class UserPreferenceServiceTest {

  @Autowired UserPreferenceService service;
  @Autowired ConfigEntryRepository configRepo;
  @Autowired ModelProviderRepository providerRepo;
  @Autowired ModelRepository modelRepo;
  @Autowired TestDbHelper dbHelper;

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private Identity identity;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    identity = new Identity(TENANT, USER, Set.of("ROLE_USER"));
  }

  @Test
  void mergeSystemTenantUserPrecedence() {
    seed("system", "system", "theme", "\"light\"");
    seed("tenant", TENANT, "theme", "\"dark\"");
    seed("user", USER, "theme", "\"midnight\"");

    UserPreferenceResponse resp = service.getEffectiveConfig(identity).block();
    assertThat(resp.entries()).containsEntry("theme", "\"midnight\"");
  }

  @Test
  void tenantOverridesSystem() {
    seed("system", "system", "language", "\"en\"");
    seed("tenant", TENANT, "language", "\"ja\"");

    UserPreferenceResponse resp = service.getEffectiveConfig(identity).block();
    assertThat(resp.entries()).containsEntry("language", "\"ja\"");
  }

  @Test
  void upsertCreatesAndUpdatesUserEntry() {
    service
        .upsertUserEntry(new UpdateUserPreferenceRequest("theme", "\"dark\""), null, identity)
        .block();
    UserPreferenceResponse afterFirst = service.getEffectiveConfig(identity).block();
    assertThat(afterFirst.entries()).containsEntry("theme", "\"dark\"");

    // Second upsert updates the same row, bumping revision
    service
        .upsertUserEntry(new UpdateUserPreferenceRequest("theme", "\"midnight\""), null, identity)
        .block();
    UserPreferenceResponse afterSecond = service.getEffectiveConfig(identity).block();
    assertThat(afterSecond.entries()).containsEntry("theme", "\"midnight\"");
  }

  @Test
  void modelPreferenceUpsertIsIdempotentAndBumpsRevision() {
    String modelId = createModel();
    service
        .setModelPreference(new UserModelPreferenceRequest(modelId, null), null, identity)
        .block();
    var first = service.getModelPreference(identity).block();
    assertThat(first.selectedModelId()).isEqualTo(modelId);
    assertThat(first.revision()).isZero();

    String newModel = createModel();
    service
        .setModelPreference(new UserModelPreferenceRequest(newModel, null), null, identity)
        .block();
    var second = service.getModelPreference(identity).block();
    assertThat(second.selectedModelId()).isEqualTo(newModel);
    assertThat(second.revision()).isEqualTo(1);
  }

  private String createModel() {
    ModelProvider provider =
        new ModelProvider(
            Ulid.next(),
            TENANT,
            null,
            "p-" + Ulid.next(),
            "P",
            null,
            "api_key",
            true,
            "{}",
            null,
            0,
            "admin",
            "admin",
            Instant.now(),
            Instant.now(),
            null);
    providerRepo.save(provider);
    Model model =
        new Model(
            Ulid.next(),
            TENANT,
            null,
            provider.id(),
            "m-" + Ulid.next(),
            "M",
            null,
            "custom",
            true,
            false,
            null,
            null,
            null,
            0,
            Instant.now(),
            Instant.now(),
            null);
    return modelRepo.save(model).id();
  }

  private void seed(String scopeType, String scopeId, String key, String valueJson) {
    configRepo.save(
        new ConfigEntry(
            Ulid.next(),
            TENANT,
            scopeType,
            scopeId,
            key,
            valueJson,
            "1",
            0,
            Instant.now(),
            Instant.now(),
            null));
  }
}
