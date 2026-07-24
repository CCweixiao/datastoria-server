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
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.domain.AgentRevision;
import io.datastoria.server.domain.Ulid;

@SpringBootTest
@ActiveProfiles("test")
class SqliteAgentDefinitionRepositoryTest {

  @Autowired AgentDefinitionRepository defRepo;
  @Autowired AgentRevisionRepository revRepo;
  @Autowired TestDbHelper dbHelper;

  private static final String TENANT = "tenant-test";

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void saveAndFindById() {
    AgentDefinition def = newDef("main", "Main Agent");
    AgentDefinition saved = defRepo.save(def);
    assertThat(saved.status()).isEqualTo("draft");
    assertThat(saved.revision()).isZero();
    assertThat(defRepo.findById(def.id(), TENANT)).isPresent();
  }

  @Test
  void createRevisionAndPublish() {
    AgentDefinition def = defRepo.save(newDef("main", "Main Agent"));
    AgentRevision rev = newRev(def.id(), 1, "You are helpful");
    revRepo.save(rev);

    defRepo.publish(def.id(), TENANT, rev.id(), 0);
    AgentDefinition published = defRepo.findById(def.id(), TENANT).orElseThrow();
    assertThat(published.status()).isEqualTo("published");
    assertThat(published.publishedRevisionId()).isEqualTo(rev.id());
    assertThat(published.revision()).isEqualTo(1);
  }

  @Test
  void disableAgent() {
    AgentDefinition def = defRepo.save(newDef("main", "Main Agent"));
    defRepo.disable(def.id(), TENANT, 0);
    AgentDefinition disabled = defRepo.findById(def.id(), TENANT).orElseThrow();
    assertThat(disabled.status()).isEqualTo("disabled");
  }

  @Test
  void publishNonExistentThrowsNotFound() {
    assertThatThrownBy(() -> defRepo.publish(Ulid.next(), TENANT, "rev", 0))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void publishWithStaleRevisionThrowsConflict() {
    AgentDefinition def = defRepo.save(newDef("main", "Main Agent"));
    AgentRevision rev = revRepo.save(newRev(def.id(), 1, "prompt"));
    assertThatThrownBy(() -> defRepo.publish(def.id(), TENANT, rev.id(), 99))
        .isInstanceOf(RevisionConflictException.class);
  }

  @Test
  void revisionsAreImmutableAndOrderedByVersion() {
    AgentDefinition def = defRepo.save(newDef("main", "Main Agent"));
    revRepo.save(newRev(def.id(), 1, "v1"));
    revRepo.save(newRev(def.id(), 2, "v2"));
    var revs = revRepo.findByAgentId(def.id(), TENANT);
    assertThat(revs).hasSize(2);
    assertThat(revs.get(0).version()).isEqualTo(1);
    assertThat(revs.get(1).version()).isEqualTo(2);
  }

  private AgentDefinition newDef(String key, String name) {
    return new AgentDefinition(
        Ulid.next(),
        TENANT,
        key,
        name,
        null,
        "draft",
        null,
        0,
        "admin",
        "admin",
        Instant.now(),
        Instant.now(),
        null);
  }

  private AgentRevision newRev(String agentId, int version, String prompt) {
    return new AgentRevision(
        Ulid.next(),
        agentId,
        version,
        null,
        prompt,
        "checksum-" + version,
        null,
        null,
        null,
        "admin",
        Instant.now());
  }
}
