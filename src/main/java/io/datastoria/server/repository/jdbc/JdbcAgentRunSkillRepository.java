package io.datastoria.server.repository.jdbc;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.agent.domain.AgentRunSkillPin;
import io.datastoria.server.repository.AgentRunSkillRepository;

@Repository
public class JdbcAgentRunSkillRepository implements AgentRunSkillRepository {

  private static final RowMapper<AgentRunSkillPin> MAPPER =
      (rs, rowNum) ->
          new AgentRunSkillPin(
              rs.getString("tenant_id"),
              rs.getString("run_id"),
              rs.getString("skill_id"),
              rs.getLong("skill_revision"),
              rs.getString("content_checksum"));

  private final JdbcClient jdbc;

  public JdbcAgentRunSkillRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void saveAll(List<AgentRunSkillPin> pins) {
    for (AgentRunSkillPin pin : pins) {
      jdbc.sql(
              """
              INSERT INTO ds_agent_run_skill
                (tenant_id, run_id, skill_id, skill_revision, content_checksum)
              VALUES (:tenantId, :runId, :skillId, :skillRevision, :contentChecksum)
              """)
          .param("tenantId", pin.tenantId())
          .param("runId", pin.runId())
          .param("skillId", pin.skillId())
          .param("skillRevision", pin.skillRevision())
          .param("contentChecksum", pin.contentChecksum())
          .update();
    }
  }

  @Override
  public List<AgentRunSkillPin> findByRun(String tenantId, String runId) {
    return jdbc.sql(
            """
            SELECT tenant_id, run_id, skill_id, skill_revision, content_checksum
            FROM ds_agent_run_skill
            WHERE tenant_id = :tenantId AND run_id = :runId
            ORDER BY skill_id
            """)
        .param("tenantId", tenantId)
        .param("runId", runId)
        .query(MAPPER)
        .list();
  }
}
