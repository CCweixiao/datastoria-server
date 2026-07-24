package io.datastoria.server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.domain.Ulid;

@SpringBootTest
@ActiveProfiles("test")
class SqliteModelRepositoryTest {

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
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("UNIQUE");

    modelRepo.softDelete(first.id(), TENANT, first.revision());
    assertThat(modelRepo.save(newModel("gpt-4", "Replacement")).modelKey()).isEqualTo("gpt-4");
  }

  private Model newModel(String key, String name) {
    return newModel(key, name, true);
  }

  private Model newModel(String key, String name, boolean enabled) {
    return new Model(
        Ulid.next(),
        TENANT,
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
}
