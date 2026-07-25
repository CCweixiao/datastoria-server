package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.domain.AgentSkill;
import io.datastoria.server.domain.AgentSkillResource;
import io.datastoria.server.repository.AgentSkillRepository;

@Repository
public class JdbcAgentSkillRepository implements AgentSkillRepository {

  private static final RowMapper<AgentSkill> SKILL_MAPPER =
      (rs, rowNum) ->
          new AgentSkill(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("owner_user_id"),
              rs.getString("content"),
              rs.getString("state"),
              rs.getString("scope"),
              rs.getString("version"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private static final RowMapper<AgentSkillResource> RESOURCE_MAPPER =
      (rs, rowNum) ->
          new AgentSkillResource(
              rs.getString("tenant_id"),
              rs.getString("skill_id"),
              rs.getString("resource_path"),
              rs.getString("content"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcAgentSkillRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<AgentSkill> findVisible(String tenantId, String userId, boolean includeDraft) {
    return jdbc.sql(
            """
            SELECT * FROM ds_agent_skill
            WHERE tenant_id = :tenantId AND deleted_at IS NULL
              AND (scope = 'global' OR owner_user_id = :userId)
              AND (:includeDraft = 1 OR state = 'published')
            ORDER BY id
            """)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("includeDraft", includeDraft ? 1 : 0)
        .query(SKILL_MAPPER)
        .list();
  }

  @Override
  public Optional<AgentSkill> findById(
      String tenantId, String userId, String id, boolean includeDraft) {
    return jdbc.sql(
            """
            SELECT * FROM ds_agent_skill
            WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
              AND (scope = 'global' OR owner_user_id = :userId)
              AND (:includeDraft = 1 OR state = 'published')
            """)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("id", id)
        .param("includeDraft", includeDraft ? 1 : 0)
        .query(SKILL_MAPPER)
        .optional();
  }

  @Override
  public AgentSkill upsert(AgentSkill skill) {
    Instant now = Instant.now();
    int updated =
        jdbc.sql(
                """
                UPDATE ds_agent_skill
                SET content = :content, state = :state, scope = :scope, version = :version,
                    revision = revision + 1, updated_at = :updatedAt, deleted_at = NULL
                WHERE tenant_id = :tenantId AND id = :id AND owner_user_id = :ownerUserId
                """)
            .param("content", skill.content())
            .param("state", skill.state())
            .param("scope", skill.scope())
            .param("version", skill.version())
            .param("updatedAt", SqlTimestamps.toParam(now))
            .param("tenantId", skill.tenantId())
            .param("id", skill.id())
            .param("ownerUserId", skill.ownerUserId())
            .update();
    if (updated == 0) {
      jdbc.sql(
              """
              INSERT INTO ds_agent_skill
                (id, tenant_id, owner_user_id, content, state, scope, version, revision,
                 created_at, updated_at)
              VALUES
                (:id, :tenantId, :ownerUserId, :content, :state, :scope, :version, 0,
                 :createdAt, :updatedAt)
              """)
          .param("id", skill.id())
          .param("tenantId", skill.tenantId())
          .param("ownerUserId", skill.ownerUserId())
          .param("content", skill.content())
          .param("state", skill.state())
          .param("scope", skill.scope())
          .param("version", skill.version())
          .param("createdAt", SqlTimestamps.toParam(now))
          .param("updatedAt", SqlTimestamps.toParam(now))
          .update();
    }
    return findById(skill.tenantId(), skill.ownerUserId(), skill.id(), true).orElseThrow();
  }

  @Override
  @Transactional
  public void replaceResources(
      String tenantId,
      String skillId,
      List<AgentSkillResource> resources,
      List<String> deletedPaths) {
    for (String path : deletedPaths) {
      jdbc.sql(
              """
              DELETE FROM ds_agent_skill_resource
              WHERE tenant_id = :tenantId AND skill_id = :skillId AND resource_path = :path
              """)
          .param("tenantId", tenantId)
          .param("skillId", skillId)
          .param("path", path)
          .update();
    }
    Instant now = Instant.now();
    for (AgentSkillResource resource : resources) {
      jdbc.sql(
              """
              DELETE FROM ds_agent_skill_resource
              WHERE tenant_id = :tenantId AND skill_id = :skillId AND resource_path = :path
              """)
          .param("tenantId", tenantId)
          .param("skillId", skillId)
          .param("path", resource.path())
          .update();
      jdbc.sql(
              """
              INSERT INTO ds_agent_skill_resource
                (tenant_id, skill_id, resource_path, content, created_at, updated_at)
              VALUES (:tenantId, :skillId, :path, :content, :createdAt, :updatedAt)
              """)
          .param("tenantId", tenantId)
          .param("skillId", skillId)
          .param("path", resource.path())
          .param("content", resource.content())
          .param("createdAt", SqlTimestamps.toParam(now))
          .param("updatedAt", SqlTimestamps.toParam(now))
          .update();
    }
  }

  @Override
  public List<AgentSkillResource> findResources(String tenantId, String skillId) {
    return jdbc.sql(
            """
            SELECT * FROM ds_agent_skill_resource
            WHERE tenant_id = :tenantId AND skill_id = :skillId
            ORDER BY resource_path
            """)
        .param("tenantId", tenantId)
        .param("skillId", skillId)
        .query(RESOURCE_MAPPER)
        .list();
  }

  @Override
  public void publish(String tenantId, String userId, String id) {
    int updated =
        jdbc.sql(
                """
                UPDATE ds_agent_skill
                SET state = 'published', revision = revision + 1, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND id = :id AND owner_user_id = :userId
                  AND deleted_at IS NULL
                """)
            .param("updatedAt", SqlTimestamps.toParam(Instant.now()))
            .param("tenantId", tenantId)
            .param("id", id)
            .param("userId", userId)
            .update();
    if (updated == 0) {
      throw new NotFoundException("AgentSkill", id);
    }
  }

  @Override
  public void delete(String tenantId, String userId, String id) {
    int updated =
        jdbc.sql(
                """
                UPDATE ds_agent_skill SET deleted_at = :deletedAt
                WHERE tenant_id = :tenantId AND id = :id AND owner_user_id = :userId
                  AND deleted_at IS NULL
                """)
            .param("deletedAt", SqlTimestamps.toParam(Instant.now()))
            .param("tenantId", tenantId)
            .param("id", id)
            .param("userId", userId)
            .update();
    if (updated == 0) {
      throw new NotFoundException("AgentSkill", id);
    }
  }
}
