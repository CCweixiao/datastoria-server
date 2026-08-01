package io.github.ccweixiao.datastoria.dao.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;
import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.error.RevisionConflictException;

@SpringBootTest
@ActiveProfiles("dev")
class MysqlModelRepositoryTest {

  @Autowired ModelRepository modelRepo;
  @Autowired ModelProviderRepository providerRepo;
  @Autowired TestDbHelper dbHelper;

  private static final String TENANT = "tenant-test";
  private String providerId;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
    ModelProvider provider =
        new ModelProvider(
            Ulid.next(),
            TENANT,
            null,
            "openai",
            "OpenAI",
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
    providerId = provider.id();
  }

  @Test
  void saveAndFindByIdRoundTrip() {
    Model m = newModel("gpt-4", "GPT-4");
    Model saved = modelRepo.save(m);
    assertThat(saved.revision()).isZero();
    Model found = modelRepo.findById(saved.id(), TENANT).orElseThrow();
    assertThat(found.modelKey()).isEqualTo("gpt-4");
    assertThat(found.isFree()).isFalse();
  }

  @Test
  void findEnabledReturnsOnlyEnabled() {
    modelRepo.save(newModel("gpt-4", "GPT-4", true));
    modelRepo.save(newModel("gpt-3.5", "GPT-3.5", false));
    assertThat(modelRepo.findEnabled(TENANT)).hasSize(1);
  }

  @Test
  void updateIncrementsRevision() {
    Model saved = modelRepo.save(newModel("gpt-4", "GPT-4"));
    Model updated = copyWith(saved, "GPT-4 Turbo", saved.revision());
    Model result = modelRepo.update(updated, 0);
    assertThat(result.revision()).isEqualTo(1);
    assertThat(result.displayName()).isEqualTo("GPT-4 Turbo");
  }

  @Test
  void updateStaleRevisionThrowsConflict() {
    Model saved = modelRepo.save(newModel("gpt-4", "GPT-4"));
    assertThatThrownBy(() -> modelRepo.update(saved, 99))
        .isInstanceOf(RevisionConflictException.class);
  }

  @Test
  void softDeleteRemovesFromQueries() {
    Model saved = modelRepo.save(newModel("gpt-4", "GPT-4"));
    modelRepo.softDelete(saved.id(), TENANT, 0);
    assertThat(modelRepo.findById(saved.id(), TENANT)).isEmpty();
    assertThat(modelRepo.findAll(TENANT)).isEmpty();
  }

  @Test
  void activeModelKeyIsUniqueButCanBeReusedAfterSoftDelete() {
    Model first = modelRepo.save(newModel("gpt-4", "GPT-4"));
    assertThatThrownBy(() -> modelRepo.save(newModel("gpt-4", "Duplicate")))
        .isInstanceOf(RuntimeException.class);

    modelRepo.softDelete(first.id(), TENANT, first.revision());
    assertThat(modelRepo.save(newModel("gpt-4", "Replacement")).modelKey()).isEqualTo("gpt-4");
  }

  @Test
  void privateModelsAreIsolatedAndMayReuseSystemModelKey() {
    Model system = modelRepo.save(newModel("gpt-4", "System GPT-4"));
    Model userA = modelRepo.save(privateModel("user-a", "gpt-4", "My GPT-4"));
    Model userB = modelRepo.save(privateModel("user-b", "gpt-4", "Other GPT-4"));

    assertThat(modelRepo.findSystemModels(TENANT))
        .extracting(Model::id)
        .containsExactly(system.id());
    assertThat(modelRepo.findUserModels(TENANT, "user-a"))
        .extracting(Model::id)
        .containsExactly(userA.id());
    assertThat(modelRepo.findAccessibleById(userB.id(), TENANT, "user-a")).isEmpty();
    assertThat(modelRepo.findEnabledAccessible(TENANT, "user-a"))
        .extracting(Model::id)
        .containsExactlyInAnyOrder(system.id(), userA.id());
  }

  private Model newModel(String key, String name) {
    return newModel(key, name, true);
  }

  private Model newModel(String key, String name, boolean enabled) {
    return new Model(
        Ulid.next(),
        TENANT,
        null,
        providerId,
        key,
        name,
        null,
        "system",
        enabled,
        false,
        "{}",
        "{}",
        null,
        0,
        Instant.now(),
        Instant.now(),
        null);
  }

  private Model copyWith(Model m, String displayName, long revision) {
    return new Model(
        m.id(),
        m.tenantId(),
        m.ownerUserId(),
        m.providerId(),
        m.modelKey(),
        displayName,
        m.description(),
        m.source(),
        m.enabled(),
        m.isFree(),
        m.capabilitiesJson(),
        m.generationDefaultsJson(),
        m.secretId(),
        revision,
        m.createdAt(),
        Instant.now(),
        null);
  }

  private Model privateModel(String owner, String key, String name) {
    Model systemShape = newModel(key, name);
    return new Model(
        systemShape.id(),
        systemShape.tenantId(),
        owner,
        systemShape.providerId(),
        systemShape.modelKey(),
        systemShape.displayName(),
        systemShape.description(),
        "custom",
        systemShape.enabled(),
        systemShape.isFree(),
        systemShape.capabilitiesJson(),
        systemShape.generationDefaultsJson(),
        systemShape.secretId(),
        systemShape.revision(),
        systemShape.createdAt(),
        systemShape.updatedAt(),
        systemShape.deletedAt());
  }
}
