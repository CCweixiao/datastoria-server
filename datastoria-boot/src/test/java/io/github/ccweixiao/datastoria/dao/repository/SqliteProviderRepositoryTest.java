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
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.RevisionConflictException;

@SpringBootTest
@ActiveProfiles("test")
class SqliteProviderRepositoryTest {

  @Autowired ModelProviderRepository repo;
  @Autowired TestDbHelper dbHelper;

  private static final String TENANT = "tenant-test";

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void saveAndFindByIdRoundTrip() {
    ModelProvider p = newProvider("openai", "OpenAI");
    ModelProvider saved = repo.save(p);
    assertThat(saved.id()).isEqualTo(p.id());
    assertThat(saved.revision()).isZero();
    assertThat(saved.createdAt()).isNotNull();

    ModelProvider found = repo.findById(p.id(), TENANT).orElseThrow();
    assertThat(found.providerKey()).isEqualTo("openai");
    assertThat(found.enabled()).isTrue();
  }

  @Test
  void findAllReturnsAllForTenant() {
    repo.save(newProvider("openai", "OpenAI"));
    repo.save(newProvider("anthropic", "Anthropic"));
    assertThat(repo.findAll(TENANT)).hasSize(2);
  }

  @Test
  void findAllExcludesOtherTenants() {
    repo.save(newProvider("openai", "OpenAI", TENANT));
    repo.save(newProvider("anthropic", "Anthropic", "tenant-other"));
    assertThat(repo.findAll(TENANT)).hasSize(1);
  }

  @Test
  void updateIncrementsRevision() {
    ModelProvider saved = repo.save(newProvider("openai", "OpenAI"));
    ModelProvider updated =
        new ModelProvider(
            saved.id(),
            saved.tenantId(),
            saved.providerKey(),
            "OpenAI Pro",
            saved.baseUrl(),
            saved.authType(),
            false,
            saved.configJson(),
            saved.secretId(),
            saved.revision(),
            "admin",
            "admin",
            saved.createdAt(),
            Instant.now(),
            null);
    ModelProvider result = repo.update(updated, 0);
    assertThat(result.revision()).isEqualTo(1);
    assertThat(result.displayName()).isEqualTo("OpenAI Pro");
    assertThat(result.enabled()).isFalse();
  }

  @Test
  void updateWithStaleRevisionThrowsConflict() {
    ModelProvider saved = repo.save(newProvider("openai", "OpenAI"));
    assertThatThrownBy(() -> repo.update(saved, 99)).isInstanceOf(RevisionConflictException.class);
  }

  @Test
  void updateNonExistentThrowsNotFound() {
    ModelProvider ghost = newProvider("openai", "Ghost");
    assertThatThrownBy(() -> repo.update(ghost, 0)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void softDeleteHidesRecord() {
    ModelProvider saved = repo.save(newProvider("openai", "OpenAI"));
    repo.softDelete(saved.id(), TENANT, 0);
    assertThat(repo.findById(saved.id(), TENANT)).isEmpty();
  }

  @Test
  void findByIdReturnsEmptyForWrongTenant() {
    repo.save(newProvider("openai", "OpenAI", TENANT));
    assertThat(repo.findById(Ulid.next(), "tenant-other")).isEmpty();
  }

  @Test
  void activeProviderKeyIsUniqueButCanBeReusedAfterSoftDelete() {
    ModelProvider first = repo.save(newProvider("openai", "OpenAI"));
    assertThatThrownBy(() -> repo.save(newProvider("openai", "Duplicate")))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("UNIQUE");

    repo.softDelete(first.id(), TENANT, first.revision());
    assertThat(repo.save(newProvider("openai", "Replacement")).providerKey()).isEqualTo("openai");
  }

  private ModelProvider newProvider(String key, String name) {
    return newProvider(key, name, TENANT);
  }

  private ModelProvider newProvider(String key, String name, String tenant) {
    return new ModelProvider(
        Ulid.next(),
        tenant,
        key,
        name,
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
  }
}
