package io.datastoria.server.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.datastoria.server.TestDbHelper;
import io.datastoria.server.domain.AgentSkill;
import io.datastoria.server.repository.AgentSkillRepository;

@SpringBootTest(properties = "datastoria.skills.seed-enabled=true")
@ActiveProfiles("test")
class BuiltinSkillProvisionerTest {

  @Autowired TestDbHelper dbHelper;
  @Autowired BuiltinSkillProvisioner provisioner;
  @Autowired AgentSkillRepository repository;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void importsBundlesIdempotentlyIntoSqlite() {
    provisioner.provision("tenant-seed");
    provisioner.provision("tenant-seed");

    assertThat(repository.findVisible("tenant-seed", "user@example.com", false)).hasSize(9);
    var clickhouse =
        repository.findById("tenant-seed", "user@example.com", "clickhouse", false).orElseThrow();
    assertThat(clickhouse.builtin()).isTrue();
    assertThat(clickhouse.bundleChecksum()).hasSize(64);
    assertThat(clickhouse.revision()).isZero();
    assertThat(repository.findResources("tenant-seed", "clickhouse"))
        .anyMatch(resource -> resource.path().equals("rules/schema-pk-plan-before-creation.md"));
  }

  @Test
  void tenantDatabaseSkillTakesPrecedenceOverSameIdSeed() {
    repository.upsert(
        new AgentSkill(
            "clickhouse",
            "tenant-override",
            "owner@example.com",
            "---\nname: tenant-clickhouse\ndescription: Tenant rules\n---\nUse tenant rules.",
            "published",
            "global",
            "tenant-v1",
            null,
            false,
            0,
            null,
            null,
            null));

    provisioner.provision("tenant-override");

    AgentSkill effective =
        repository
            .findById("tenant-override", "reader@example.com", "clickhouse", false)
            .orElseThrow();
    assertThat(effective.ownerUserId()).isEqualTo("owner@example.com");
    assertThat(effective.content()).contains("Use tenant rules.");
    assertThat(effective.builtin()).isFalse();
  }
}
