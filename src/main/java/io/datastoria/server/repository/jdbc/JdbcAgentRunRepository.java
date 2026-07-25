package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.IllegalRunTransitionException;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.repository.AgentRunRepository;

/**
 * JDBC implementation of {@link AgentRunRepository} for {@code ds_agent_run}. All reads/writes
 * filter by {@code tenant_id}; the only exception is the internal {@link #applyCancellation} lookup
 * by run id (globally-unique ULID), which still performs a tenant-scoped {@code UPDATE}.
 *
 * <p>State transitions use a conditional {@code UPDATE ... WHERE revision = :expected} (optimistic
 * lock). A transition that finds the run already in the target status is an idempotent success; one
 * that loses a concurrent race re-reads the row and either treats the same target as idempotent or
 * rejects the now-unreachable transition — terminal states can never be overwritten.
 */
@Repository
public class JdbcAgentRunRepository implements AgentRunRepository {

  private static final RowMapper<AgentRun> MAPPER =
      (rs, rowNum) ->
          new AgentRun(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("user_id"),
              rs.getString("session_id"),
              rs.getString("message_id"),
              rs.getString("agent_revision_id"),
              rs.getString("model_id"),
              AgentRunStatus.fromDbValue(rs.getString("status")),
              rs.getString("idempotency_key"),
              rs.getString("request_id"),
              rs.getString("connection_id"),
              rs.getString("input_snapshot_json"),
              rs.getString("usage_json"),
              rs.getString("error_code"),
              rs.getString("safe_message"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "started_at"),
              SqlTimestamps.fromParam(rs, "finished_at"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcAgentRunRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AgentRun create(AgentRun r) {
    Instant now = r.createdAt() != null ? r.createdAt() : Instant.now();
    Instant updated = r.updatedAt() != null ? r.updatedAt() : now;
    jdbc.sql(
            "INSERT INTO ds_agent_run"
                + " (id, tenant_id, user_id, session_id, message_id, agent_revision_id, model_id,"
                + "  status, idempotency_key, request_id, connection_id, input_snapshot_json,"
                + "  usage_json, error_code, safe_message, revision, started_at, finished_at,"
                + "  created_at, updated_at)"
                + " VALUES (:id,:tenant,:user,:session,:message,:arev,:model,:status,:idem,:req,"
                + "  :conn,:inputJson,:usageJson,:errorCode,:safeMessage,:revision,:started,"
                + "  :finished,:created,:updated)")
        .param("id", r.id())
        .param("tenant", r.tenantId())
        .param("user", r.userId())
        .param("session", r.sessionId())
        .param("message", r.messageId())
        .param("arev", r.agentRevisionId())
        .param("model", r.modelId())
        .param("status", r.status().dbValue())
        .param("idem", r.idempotencyKey())
        .param("req", r.requestId())
        .param("conn", r.connectionId())
        .param("inputJson", r.inputSnapshotJson())
        .param("usageJson", r.usageJson())
        .param("errorCode", r.errorCode())
        .param("safeMessage", r.safeMessage())
        .param("revision", r.revision())
        .param("started", SqlTimestamps.toParam(r.startedAt()))
        .param("finished", SqlTimestamps.toParam(r.finishedAt()))
        .param("created", SqlTimestamps.toParam(now))
        .param("updated", SqlTimestamps.toParam(updated))
        .update();
    return find(r.tenantId(), r.id()).orElseThrow(() -> new NotFoundException("AgentRun", r.id()));
  }

  @Override
  public Optional<AgentRun> find(String tenantId, String runId) {
    return jdbc.sql("SELECT * FROM ds_agent_run WHERE id = :id AND tenant_id = :tenant")
        .param("id", runId)
        .param("tenant", tenantId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public Optional<AgentRun> findByIdempotencyKey(
      String tenantId, String userId, String idempotencyKey) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_run"
                + " WHERE tenant_id = :tenant AND user_id = :user AND idempotency_key = :idem")
        .param("tenant", tenantId)
        .param("user", userId)
        .param("idem", idempotencyKey)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<AgentRun> findBySession(String tenantId, String sessionId) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_run"
                + " WHERE tenant_id = :tenant AND session_id = :session"
                + " ORDER BY created_at ASC, id ASC")
        .param("tenant", tenantId)
        .param("session", sessionId)
        .query(MAPPER)
        .list();
  }

  @Override
  public boolean transition(
      String tenantId, String runId, AgentRunStatus to, RunTransition payload) {
    AgentRun run =
        find(tenantId, runId).orElseThrow(() -> new NotFoundException("AgentRun", runId));
    return doTransition(run, to, payload);
  }

  @Override
  public boolean applyCancellation(String runId, Instant cancelledAt) {
    // Internal observer path: runId is a globally-unique ULID. Resolve the row (and thus its
    // tenant), then delegate to the tenant-scoped transition — the UPDATE still filters by
    // tenant_id.
    AgentRun run = findByIdInternal(runId);
    if (run == null) {
      return false;
    }
    try {
      return doTransition(run, AgentRunStatus.CANCELLED, RunTransition.cancelling(cancelledAt));
    } catch (IllegalRunTransitionException e) {
      // A late cancel arriving after the run reached a non-cancellable terminal state (succeeded /
      // failed / expired) is a safe no-op returning false, never an exception: the observer must be
      // safe to fire regardless of ordering.
      return false;
    }
  }

  private AgentRun findByIdInternal(String runId) {
    return jdbc.sql("SELECT * FROM ds_agent_run WHERE id = :id")
        .param("id", runId)
        .query(MAPPER)
        .optional()
        .orElse(null);
  }

  private boolean doTransition(AgentRun run, AgentRunStatus to, RunTransition payload) {
    if (run.status() == to) {
      return true; // idempotent: already in the target status
    }
    if (!run.status().canTransitionTo(to)) {
      throw new IllegalRunTransitionException(run.id(), run.status(), to);
    }
    // Only SET payload fields that are present, so unrelated columns keep their value and no NULL
    // is
    // bound (matches the project's dynamic-SQL lookup/update convention).
    StringBuilder sql =
        new StringBuilder(
            "UPDATE ds_agent_run SET status = :status, revision = revision + 1, updated_at = :now");
    if (payload.startedAt() != null) {
      sql.append(", started_at = :startedAt");
    }
    if (payload.finishedAt() != null) {
      sql.append(", finished_at = :finishedAt");
    }
    if (payload.errorCode() != null) {
      sql.append(", error_code = :errorCode");
    }
    if (payload.safeMessage() != null) {
      sql.append(", safe_message = :safeMessage");
    }
    if (payload.usageJson() != null) {
      sql.append(", usage_json = :usageJson");
    }
    sql.append(" WHERE id = :id AND tenant_id = :tenant AND revision = :expectedRevision");

    var stmt =
        jdbc.sql(sql.toString())
            .param("status", to.dbValue())
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", run.id())
            .param("tenant", run.tenantId())
            .param("expectedRevision", run.revision());
    if (payload.startedAt() != null) {
      stmt.param("startedAt", SqlTimestamps.toParam(payload.startedAt()));
    }
    if (payload.finishedAt() != null) {
      stmt.param("finishedAt", SqlTimestamps.toParam(payload.finishedAt()));
    }
    if (payload.errorCode() != null) {
      stmt.param("errorCode", payload.errorCode());
    }
    if (payload.safeMessage() != null) {
      stmt.param("safeMessage", payload.safeMessage());
    }
    if (payload.usageJson() != null) {
      stmt.param("usageJson", payload.usageJson());
    }
    int updated = stmt.update();

    if (updated == 1) {
      return true;
    }
    // Concurrent transition bumped revision. Re-read: if it landed on `to`, idempotent; otherwise
    // the run is now unreachable for `to` — refuse without overwriting the terminal state.
    AgentRun current = findByIdInternal(run.id());
    AgentRunStatus currentStatus = current == null ? run.status() : current.status();
    if (currentStatus == to) {
      return true;
    }
    throw new IllegalRunTransitionException(run.id(), currentStatus, to);
  }
}
