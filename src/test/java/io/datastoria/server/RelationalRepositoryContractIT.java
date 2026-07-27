package io.datastoria.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.agent.domain.AgentCheckpoint;
import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.CheckpointType;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.domain.AgentRevision;
import io.datastoria.server.domain.AuditLog;
import io.datastoria.server.domain.FeedbackEvent;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.repository.AgentCheckpointRepository;
import io.datastoria.server.repository.AgentDefinitionRepository;
import io.datastoria.server.repository.AgentRevisionRepository;
import io.datastoria.server.repository.AgentRunRepository;
import io.datastoria.server.repository.AuditLogRepository;
import io.datastoria.server.repository.FeedbackEventRepository;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.repository.ModelRepository;

/** Shared repository contract executed by the MySQL Testcontainers suite. */
abstract class RelationalRepositoryContractIT {

  @Autowired AgentDefinitionRepository agentDefRepo;
  @Autowired AgentRevisionRepository agentRevRepo;
  @Autowired ModelProviderRepository providerRepo;
  @Autowired AgentRunRepository runRepo;
  @Autowired AgentCheckpointRepository checkpointRepo;
  @Autowired AuditLogRepository auditRepo;
  @Autowired FeedbackEventRepository feedbackRepo;
  @Autowired ModelRepository modelRepo;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper objectMapper;

  protected abstract String tenantId();

  @Test
  void providerCrud() {
    String tenant = tenantId();
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
  void agentDefinitionPublish() {
    String tenant = tenantId();
    AgentDefinition definition =
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
    agentDefRepo.save(definition);
    AgentRevision revision =
        new AgentRevision(
            Ulid.next(),
            definition.id(),
            1,
            null,
            "prompt",
            "checksum",
            null,
            null,
            null,
            "admin",
            Instant.now());
    agentRevRepo.save(revision);
    agentDefRepo.publish(definition.id(), tenant, revision.id(), 0);
    AgentDefinition published = agentDefRepo.findById(definition.id(), tenant).orElseThrow();
    assertThat(published.status()).isEqualTo("published");
    assertThat(published.publishedRevisionId()).isEqualTo(revision.id());
    assertThat(published.updatedAt()).isNotNull();
  }

  @Test
  void agentRunStateMachine() {
    String tenant = tenantId();
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
    long revision = runRepo.find(tenant, run.id()).orElseThrow().revision();
    runRepo.transition(
        tenant, run.id(), AgentRunStatus.SUCCEEDED, RunTransition.completing(now, null));
    AgentRun done = runRepo.find(tenant, run.id()).orElseThrow();
    assertThat(done.status()).isEqualTo(AgentRunStatus.SUCCEEDED);
    assertThat(done.revision()).isEqualTo(revision);
  }

  @Test
  void agentCheckpointUpsert() throws Exception {
    String tenant = tenantId();
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

    checkpointRepo.save(checkpoint(tenant, runId, 1, "{\"v\":1}", "1", now));
    checkpointRepo.save(checkpoint(tenant, runId, 1, "{\"v\":2}", "2", now));
    checkpointRepo.save(checkpoint(tenant, runId, 2, "{\"v\":3}", "3", now));

    var all = checkpointRepo.findAllByRun(tenant, runId);
    assertThat(all).hasSize(2);
    String latestState = checkpointRepo.findLatest(tenant, runId).orElseThrow().stateJson();
    assertThat(objectMapper.readTree(latestState).path("v").asInt()).isEqualTo(3);
  }

  @Test
  void generatedAuditKey() {
    AuditLog saved =
        auditRepo.save(
            new AuditLog(
                null,
                tenantId(),
                "admin",
                "contract.test",
                "repository",
                "generated-key",
                "request",
                null,
                "success",
                Instant.now()));

    assertThat(saved.id()).isPositive();
  }

  @Test
  void modelBooleanColumnsAndEnabledQuery() {
    String tenant = tenantId();
    String providerId = Ulid.next();
    providerRepo.save(provider(tenant, providerId));
    Instant now = Instant.now();
    Model saved =
        modelRepo.save(
            new Model(
                Ulid.next(),
                tenant,
                providerId,
                "model-contract",
                "Model Contract",
                null,
                "custom",
                true,
                false,
                "{}",
                "{}",
                null,
                0,
                now,
                now,
                null));

    assertThat(saved.enabled()).isTrue();
    assertThat(saved.isFree()).isFalse();
    assertThat(modelRepo.findEnabled(tenant)).extracting(Model::id).contains(saved.id());
  }

  @Test
  void feedbackBooleanColumnsRoundTrip() {
    String tenant = tenantId();
    insertSession("sess_feedback", tenant);
    Instant now = Instant.now();
    FeedbackEvent saved =
        feedbackRepo.upsert(
            new FeedbackEvent(
                Ulid.next(),
                tenant,
                "admin",
                "auto_explain_error",
                "sess_feedback",
                "message-feedback",
                false,
                "other",
                "{\"errorCode\":\"60\"}",
                null,
                true,
                now,
                now));

    assertThat(saved.solved()).isFalse();
    assertThat(saved.recoveryActionTaken()).isTrue();
  }

  private ModelProvider provider(String tenant, String id) {
    Instant now = Instant.now();
    return new ModelProvider(
        id,
        tenant,
        "openai-" + id,
        "OpenAI",
        null,
        "api_key",
        true,
        "{}",
        null,
        0,
        "admin",
        "admin",
        now,
        now,
        null);
  }

  private AgentCheckpoint checkpoint(
      String tenant, String runId, long sequence, String state, String checksumChar, Instant now) {
    return new AgentCheckpoint(
        Ulid.next(),
        tenant,
        runId,
        sequence,
        CheckpointType.RUN_STATE,
        state,
        "v1",
        checksumChar.repeat(64),
        now,
        now);
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
        .param("now", Timestamp.from(now))
        .update();
  }
}
