package io.datastoria.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import io.datastoria.server.agent.domain.AgentCheckpoint;
import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.CheckpointType;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.domain.AgentRevision;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.repository.AgentCheckpointRepository;
import io.datastoria.server.repository.AgentDefinitionRepository;
import io.datastoria.server.repository.AgentRevisionRepository;
import io.datastoria.server.repository.AgentRunRepository;
import io.datastoria.server.repository.ModelProviderRepository;

/**
 * Runs the repository contract against a real MySQL 8.0 instance via Testcontainers. Verifies that
 * the dual-dialect DDL and {@code SqlTimestamps} ISO-8601 pattern work correctly on MySQL, not just
 * SQLite. Skipped automatically when Docker is unavailable.
 */
@SpringBootTest
@ActiveProfiles("mysql-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MysqlRepositoryIT {

  private static MySQLContainer<?> mysql;
  private static boolean dockerAvailable = false;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    try {
      mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("datastoria_it");
      mysql.start();
      registry.add("spring.datasource.url", mysql::getJdbcUrl);
      registry.add("spring.datasource.username", mysql::getUsername);
      registry.add("spring.datasource.password", mysql::getPassword);
      registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
      registry.add("spring.flyway.locations", () -> "classpath:db/migration/mysql");
      dockerAvailable = true;
    } catch (Exception e) {
      // Fall back to SQLite so the Spring context still loads; tests will be skipped.
      registry.add("spring.datasource.url", () -> "jdbc:sqlite::memory:");
      registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
      registry.add("spring.flyway.locations", () -> "classpath:db/migration/sqlite");
      dockerAvailable = false;
    }
  }

  @BeforeAll
  void checkDocker() {
    Assumptions.assumeTrue(dockerAvailable, "Docker unavailable — skipping MySQL IT");
  }

  @Autowired AgentDefinitionRepository agentDefRepo;
  @Autowired AgentRevisionRepository agentRevRepo;
  @Autowired ModelProviderRepository providerRepo;
  @Autowired AgentRunRepository runRepo;
  @Autowired AgentCheckpointRepository checkpointRepo;
  @Autowired JdbcClient jdbc;

  @Test
  void providerCrudOnMysql() {
    String tenant = "tenant-mysql-it";
    ModelProvider provider =
        new ModelProvider(
            Ulid.next(),
            tenant,
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
    Optional<ModelProvider> found = providerRepo.findById(provider.id(), tenant);
    assertThat(found).isPresent();
    assertThat(found.get().displayName()).isEqualTo("OpenAI");
    assertThat(found.get().createdAt()).isNotNull();
  }

  @Test
  void agentDefinitionPublishOnMysql() {
    String tenant = "tenant-mysql-it";
    AgentDefinition def =
        new AgentDefinition(
            Ulid.next(),
            tenant,
            "main",
            "Main",
            null,
            "draft",
            null,
            0,
            "admin",
            "admin",
            Instant.now(),
            Instant.now(),
            null);
    agentDefRepo.save(def);
    AgentRevision rev =
        new AgentRevision(
            Ulid.next(),
            def.id(),
            1,
            null,
            "prompt",
            "checksum",
            null,
            null,
            null,
            "admin",
            Instant.now());
    agentRevRepo.save(rev);
    agentDefRepo.publish(def.id(), tenant, rev.id(), 0);
    AgentDefinition published = agentDefRepo.findById(def.id(), tenant).orElseThrow();
    assertThat(published.status()).isEqualTo("published");
    assertThat(published.publishedRevisionId()).isEqualTo(rev.id());
    assertThat(published.updatedAt()).isNotNull();
  }

  @Test
  void agentRunStateMachineOnMysql() {
    String tenant = "tenant-mysql-it";
    insertSession("sess_run", tenant);
    Instant now = Instant.now();
    AgentRun run =
        new AgentRun(
            Ulid.next(),
            tenant,
            "admin",
            "sess_run",
            null,
            "arev",
            "mdl",
            AgentRunStatus.RUNNING,
            "idem-run",
            "idem-run",
            null,
            null,
            null,
            null,
            null,
            0L,
            now,
            null,
            now,
            now);
    runRepo.create(run);

    assertThat(
            runRepo.transition(
                tenant,
                run.id(),
                AgentRunStatus.SUCCEEDED,
                RunTransition.completing(now, "{\"out\":5}")))
        .isTrue();
    // Idempotent re-complete does not bump revision.
    long rev = runRepo.find(tenant, run.id()).orElseThrow().revision();
    runRepo.transition(
        tenant, run.id(), AgentRunStatus.SUCCEEDED, RunTransition.completing(now, null));
    AgentRun done = runRepo.find(tenant, run.id()).orElseThrow();
    assertThat(done.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(done.revision()).isEqualTo(rev);
  }

  @Test
  void agentCheckpointUpsertOnMysql() {
    String tenant = "tenant-mysql-it";
    insertSession("sess_cp", tenant);
    Instant now = Instant.now();
    String runId = Ulid.next();
    runRepo.create(
        new AgentRun(
            runId,
            tenant,
            "admin",
            "sess_cp",
            null,
            "arev",
            "mdl",
            AgentRunStatus.RUNNING,
            "idem-cp",
            "idem-cp",
            null,
            null,
            null,
            null,
            null,
            0L,
            now,
            null,
            now,
            now));

    checkpointRepo.save(
        new AgentCheckpoint(
            Ulid.next(),
            tenant,
            runId,
            1,
            CheckpointType.RUN_STATE,
            "{\"v\":1}",
            "v1",
            "1".repeat(64),
            now,
            now));
    // Overwrite at sequence 1 (new id, new content).
    checkpointRepo.save(
        new AgentCheckpoint(
            Ulid.next(),
            tenant,
            runId,
            1,
            CheckpointType.RUN_STATE,
            "{\"v\":2}",
            "v1",
            "2".repeat(64),
            now,
            now));
    // Append at sequence 2.
    checkpointRepo.save(
        new AgentCheckpoint(
            Ulid.next(),
            tenant,
            runId,
            2,
            CheckpointType.RUN_STATE,
            "{\"v\":3}",
            "v1",
            "3".repeat(64),
            now,
            now));

    var all = checkpointRepo.findAllByRun(tenant, runId);
    assertThat(all).hasSize(2);
    assertThat(checkpointRepo.findLatest(tenant, runId).orElseThrow().stateJson())
        .contains("\"v\":3");
  }

  private void insertSession(String id, String tenant) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_chat_session"
                + " (id, tenant_id, user_id, connection_id, title, revision, created_at, updated_at)"
                + " VALUES (:id,:t,:u,'ch','t',0,:now,:now)")
        .param("id", id)
        .param("t", tenant)
        .param("u", "admin")
        .param("now", now.toString())
        .update();
  }
}
