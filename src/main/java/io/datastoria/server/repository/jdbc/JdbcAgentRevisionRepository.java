package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.AgentRevision;
import io.datastoria.server.repository.AgentRevisionRepository;

@Repository
public class JdbcAgentRevisionRepository implements AgentRevisionRepository {

  private static final RowMapper<AgentRevision> MAPPER =
      (rs, rowNum) ->
          new AgentRevision(
              rs.getString("id"),
              rs.getString("agent_id"),
              rs.getInt("version"),
              rs.getString("model_id"),
              rs.getString("system_prompt"),
              rs.getString("prompt_checksum"),
              rs.getString("runtime_config_json"),
              rs.getString("tool_policy_json"),
              rs.getString("skill_policy_json"),
              rs.getString("created_by"),
              SqlTimestamps.fromParam(rs, "created_at"));

  private final JdbcClient jdbc;

  public JdbcAgentRevisionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AgentRevision save(AgentRevision r) {
    Instant createdAt = r.createdAt() != null ? r.createdAt() : Instant.now();
    jdbc.sql(
            "INSERT INTO ds_agent_revision (id, agent_id, version, model_id, system_prompt,"
                + " prompt_checksum, runtime_config_json, tool_policy_json,"
                + " skill_policy_json, created_by, created_at)"
                + " VALUES (:id, :agentId, :version, :modelId, :systemPrompt,"
                + " :promptChecksum, :runtimeConfigJson, :toolPolicyJson,"
                + " :skillPolicyJson, :createdBy, :createdAt)")
        .param("id", r.id())
        .param("agentId", r.agentId())
        .param("version", r.version())
        .param("modelId", r.modelId())
        .param("systemPrompt", r.systemPrompt())
        .param("promptChecksum", r.promptChecksum())
        .param("runtimeConfigJson", r.runtimeConfigJson())
        .param("toolPolicyJson", r.toolPolicyJson())
        .param("skillPolicyJson", r.skillPolicyJson())
        .param("createdBy", r.createdBy())
        .param("createdAt", SqlTimestamps.toParam(createdAt))
        .update();
    return new AgentRevision(
        r.id(),
        r.agentId(),
        r.version(),
        r.modelId(),
        r.systemPrompt(),
        r.promptChecksum(),
        r.runtimeConfigJson(),
        r.toolPolicyJson(),
        r.skillPolicyJson(),
        r.createdBy(),
        createdAt);
  }

  @Override
  public Optional<AgentRevision> findById(String id, String tenantId) {
    return jdbc.sql(
            "SELECT r.* FROM ds_agent_revision r"
                + " JOIN ds_agent_definition d ON d.id = r.agent_id"
                + " WHERE r.id = :id AND d.tenant_id = :tenantId AND d.deleted_at IS NULL")
        .param("id", id)
        .param("tenantId", tenantId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<AgentRevision> findByAgentId(String agentId, String tenantId) {
    return jdbc.sql(
            "SELECT r.* FROM ds_agent_revision r"
                + " JOIN ds_agent_definition d ON d.id = r.agent_id"
                + " WHERE r.agent_id = :agentId AND d.tenant_id = :tenantId"
                + " AND d.deleted_at IS NULL ORDER BY r.version")
        .param("agentId", agentId)
        .param("tenantId", tenantId)
        .query(MAPPER)
        .list();
  }
}
