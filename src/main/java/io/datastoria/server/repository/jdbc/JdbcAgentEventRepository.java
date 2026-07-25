package io.datastoria.server.repository.jdbc;

import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.agent.domain.PersistedAgentFrame;
import io.datastoria.server.repository.AgentEventRepository;

@Repository
public class JdbcAgentEventRepository implements AgentEventRepository {

  private static final RowMapper<PersistedAgentFrame> MAPPER =
      (rs, ignored) ->
          new PersistedAgentFrame(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("run_id"),
              rs.getLong("sequence"),
              rs.getString("frame_text"),
              SqlTimestamps.fromParam(rs, "created_at"));

  private final JdbcClient jdbc;

  public JdbcAgentEventRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void append(PersistedAgentFrame frame) {
    jdbc.sql(
            "INSERT INTO ds_agent_event"
                + " (id,tenant_id,run_id,sequence,frame_text,created_at)"
                + " VALUES (:id,:tenant,:run,:seq,:frame,:created)")
        .param("id", frame.id())
        .param("tenant", frame.tenantId())
        .param("run", frame.runId())
        .param("seq", frame.sequence())
        .param("frame", frame.frameText())
        .param("created", SqlTimestamps.toParam(frame.createdAt()))
        .update();
  }

  @Override
  public List<PersistedAgentFrame> findAfter(String tenantId, String runId, long sequence) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_event"
                + " WHERE tenant_id=:tenant AND run_id=:run AND sequence>:seq"
                + " ORDER BY sequence ASC")
        .param("tenant", tenantId)
        .param("run", runId)
        .param("seq", sequence)
        .query(MAPPER)
        .list();
  }
}
