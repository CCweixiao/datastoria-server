package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.agent.domain.AgentCheckpoint;
import io.datastoria.server.agent.domain.CheckpointType;
import io.datastoria.server.repository.AgentCheckpointRepository;

/**
 * JDBC implementation of {@link AgentCheckpointRepository} for {@code ds_agent_checkpoint}. Uses a
 * dialect-neutral update-then-insert upsert (no dialect-specific {@code ON CONFLICT}/{@code ON
 * DUPLICATE KEY}): {@link #save} overwrites the row at {@code (tenantId, runId, sequence)} if it
 * exists (preserving {@code created_at}), otherwise inserts. An insert race retries the update. All
 * reads filter by {@code tenant_id}.
 *
 * <p>{@code stateJson} is opaque to this layer — never an AgentScope {@code State} type — and the
 * adapter that produces it must exclude prompt, API key, and provider credential.
 */
@Repository
public class JdbcAgentCheckpointRepository implements AgentCheckpointRepository {

  private static final RowMapper<AgentCheckpoint> MAPPER =
      (rs, rowNum) ->
          new AgentCheckpoint(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("run_id"),
              rs.getLong("sequence"),
              CheckpointType.fromDbValue(rs.getString("checkpoint_type")),
              rs.getString("state_json"),
              rs.getString("codec_version"),
              rs.getString("checksum"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcAgentCheckpointRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void save(AgentCheckpoint c) {
    Instant now = Instant.now();
    if (updateExisting(c, now) == 1) {
      return;
    }
    try {
      jdbc.sql(
              "INSERT INTO ds_agent_checkpoint"
                  + " (id, tenant_id, run_id, sequence, checkpoint_type, state_json, codec_version,"
                  + "  checksum, created_at, updated_at)"
                  + " VALUES (:id,:tenant,:run,:seq,:type,:state,:codec,:checksum,:now,:now)")
          .param("id", c.id())
          .param("tenant", c.tenantId())
          .param("run", c.runId())
          .param("seq", c.sequence())
          .param("type", c.checkpointType().dbValue())
          .param("state", c.stateJson())
          .param("codec", c.codecVersion())
          .param("checksum", c.checksum())
          .param("now", SqlTimestamps.toParam(now))
          .update();
    } catch (RuntimeException insertFailure) {
      // Another writer may have inserted the same logical checkpoint after our UPDATE found no
      // row. Retrying UPDATE makes the operation an atomic, dialect-neutral upsert. If no row now
      // exists, the INSERT failed for a different reason (FK / JSON / other constraint), so retain
      // the original failure instead of hiding it.
      if (updateExisting(c, Instant.now()) == 1) {
        return;
      }
      throw insertFailure;
    }
  }

  private int updateExisting(AgentCheckpoint c, Instant updatedAt) {
    return jdbc.sql(
            "UPDATE ds_agent_checkpoint"
                + " SET checkpoint_type = :type, state_json = :state, codec_version = :codec,"
                + " checksum = :checksum, updated_at = :now"
                + " WHERE tenant_id = :tenant AND run_id = :run AND sequence = :seq")
        .param("type", c.checkpointType().dbValue())
        .param("state", c.stateJson())
        .param("codec", c.codecVersion())
        .param("checksum", c.checksum())
        .param("now", SqlTimestamps.toParam(updatedAt))
        .param("tenant", c.tenantId())
        .param("run", c.runId())
        .param("seq", c.sequence())
        .update();
  }

  @Override
  public Optional<AgentCheckpoint> findLatest(String tenantId, String runId) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_checkpoint"
                + " WHERE tenant_id = :tenant AND run_id = :run"
                + " ORDER BY sequence DESC LIMIT 1")
        .param("tenant", tenantId)
        .param("run", runId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public Optional<AgentCheckpoint> findBySequence(String tenantId, String runId, long sequence) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_checkpoint"
                + " WHERE tenant_id = :tenant AND run_id = :run AND sequence = :seq")
        .param("tenant", tenantId)
        .param("run", runId)
        .param("seq", sequence)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<AgentCheckpoint> findAllByRun(String tenantId, String runId) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_checkpoint"
                + " WHERE tenant_id = :tenant AND run_id = :run ORDER BY sequence ASC")
        .param("tenant", tenantId)
        .param("run", runId)
        .query(MAPPER)
        .list();
  }
}
