package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.PendingActionConflictException;
import io.datastoria.server.agent.domain.PendingActionExpiredException;
import io.datastoria.server.agent.domain.PendingActionResolution;
import io.datastoria.server.agent.domain.PendingActionStatus;
import io.datastoria.server.agent.domain.PendingActionType;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.repository.AgentPendingActionRepository;

/** JDBC pending-action store with owner-scoped reads and revision-guarded resolution. */
@Repository
public class JdbcAgentPendingActionRepository implements AgentPendingActionRepository {

  private static final RowMapper<AgentPendingAction> MAPPER =
      (rs, rowNum) ->
          new AgentPendingAction(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("run_id"),
              rs.getString("tool_call_id"),
              PendingActionType.fromDbValue(rs.getString("action_type")),
              rs.getString("request_json"),
              rs.getString("response_json"),
              rs.getString("resolution_digest"),
              PendingActionStatus.fromDbValue(rs.getString("status")),
              SqlTimestamps.fromParam(rs, "expires_at"),
              rs.getString("resolved_by"),
              SqlTimestamps.fromParam(rs, "resolved_at"),
              rs.getLong("revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"));

  private final JdbcClient jdbc;

  public JdbcAgentPendingActionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  @Transactional
  public AgentPendingAction create(String userId, AgentPendingAction a) {
    Optional<AgentPendingAction> existing =
        findByToolCall(a.tenantId(), userId, a.runId(), a.toolCallId());
    if (existing.isPresent()) {
      AgentPendingAction row = existing.orElseThrow();
      if (row.id().equals(a.id())
          && row.actionType() == a.actionType()
          && row.requestJson().equals(a.requestJson())) {
        return row;
      }
      throw new PendingActionConflictException(row.id());
    }
    Instant created = a.createdAt() != null ? a.createdAt() : Instant.now();
    Instant updated = a.updatedAt() != null ? a.updatedAt() : created;
    int inserted =
        jdbc.sql(
                "INSERT INTO ds_agent_pending_action"
                    + " (id,tenant_id,run_id,tool_call_id,action_type,request_json,response_json,"
                    + " resolution_digest,status,expires_at,resolved_by,resolved_at,revision,"
                    + " created_at,updated_at)"
                    + " SELECT :id,:tenant,:run,:toolCall,:type,:requestJson,:responseJson,"
                    + " :digest,:status,:expires,:resolvedBy,:resolvedAt,:revision,:created,:updated"
                    + " FROM ds_agent_run"
                    + " WHERE id=:run AND tenant_id=:tenant AND user_id=:user")
            .param("id", a.id())
            .param("tenant", a.tenantId())
            .param("run", a.runId())
            .param("user", userId)
            .param("toolCall", a.toolCallId())
            .param("type", a.actionType().dbValue())
            .param("requestJson", a.requestJson())
            .param("responseJson", a.responseJson())
            .param("digest", a.resolutionDigest())
            .param("status", a.status().dbValue())
            .param("expires", SqlTimestamps.toParam(a.expiresAt()))
            .param("resolvedBy", a.resolvedBy())
            .param("resolvedAt", SqlTimestamps.toParam(a.resolvedAt()))
            .param("revision", a.revision())
            .param("created", SqlTimestamps.toParam(created))
            .param("updated", SqlTimestamps.toParam(updated))
            .update();
    if (inserted != 1) {
      throw new NotFoundException("AgentRun", a.runId());
    }
    return find(a.tenantId(), userId, a.runId(), a.id())
        .orElseThrow(() -> new NotFoundException("PendingAction", a.id()));
  }

  @Override
  public Optional<AgentPendingAction> find(
      String tenantId, String userId, String runId, String actionId) {
    return findScoped(tenantId, userId, runId, "a.id=:value", actionId);
  }

  @Override
  public Optional<AgentPendingAction> findByToolCall(
      String tenantId, String userId, String runId, String toolCallId) {
    return findScoped(tenantId, userId, runId, "a.tool_call_id=:value", toolCallId);
  }

  private Optional<AgentPendingAction> findScoped(
      String tenantId, String userId, String runId, String predicate, String value) {
    return jdbc.sql(
            "SELECT a.* FROM ds_agent_pending_action a"
                + " JOIN ds_agent_run r ON r.tenant_id=a.tenant_id AND r.id=a.run_id"
                + " WHERE a.tenant_id=:tenant AND a.run_id=:run AND "
                + predicate
                + " AND r.user_id=:user")
        .param("tenant", tenantId)
        .param("run", runId)
        .param("value", value)
        .param("user", userId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<AgentPendingAction> findPending(String tenantId, String userId, String runId) {
    return jdbc.sql(
            "SELECT a.* FROM ds_agent_pending_action a"
                + " JOIN ds_agent_run r ON r.tenant_id=a.tenant_id AND r.id=a.run_id"
                + " WHERE a.tenant_id=:tenant AND a.run_id=:run AND r.user_id=:user"
                + " AND a.status='pending' ORDER BY a.created_at,a.id")
        .param("tenant", tenantId)
        .param("run", runId)
        .param("user", userId)
        .query(MAPPER)
        .list();
  }

  @Override
  public AgentPendingAction resolve(
      String tenantId,
      String userId,
      String runId,
      String actionId,
      PendingActionResolution resolution) {
    AgentPendingAction current =
        find(tenantId, userId, runId, actionId)
            .orElseThrow(() -> new NotFoundException("PendingAction", actionId));
    if (!resolution.status().isValidResolutionFor(current.actionType())) {
      throw new IllegalArgumentException(
          "Resolution " + resolution.status() + " is invalid for " + current.actionType());
    }
    if (current.status().isTerminal()) {
      return idempotentOrConflict(current, resolution);
    }
    if (!current.expiresAt().isAfter(resolution.resolvedAt())) {
      expireOne(current, resolution.resolvedAt());
      throw new PendingActionExpiredException(actionId);
    }

    int updated =
        jdbc.sql(
                "UPDATE ds_agent_pending_action SET status=:status,response_json=:response,"
                    + " resolution_digest=:digest,resolved_by=:actor,resolved_at=:resolved,"
                    + " revision=revision+1,updated_at=:resolved"
                    + " WHERE id=:action AND tenant_id=:tenant AND run_id=:run"
                    + " AND status='pending' AND revision=:revision AND expires_at>:resolved"
                    + " AND EXISTS (SELECT 1 FROM ds_agent_run r WHERE r.id=:run"
                    + " AND r.tenant_id=:tenant AND r.user_id=:user)")
            .param("status", resolution.status().dbValue())
            .param("response", resolution.responseJson())
            .param("digest", resolution.digest())
            .param("actor", resolution.resolvedBy())
            .param("resolved", SqlTimestamps.toParam(resolution.resolvedAt()))
            .param("action", actionId)
            .param("tenant", tenantId)
            .param("run", runId)
            .param("user", userId)
            .param("revision", current.revision())
            .update();
    if (updated == 1) {
      return find(tenantId, userId, runId, actionId).orElseThrow();
    }
    AgentPendingAction raced =
        find(tenantId, userId, runId, actionId)
            .orElseThrow(() -> new NotFoundException("PendingAction", actionId));
    if (raced.status() == PendingActionStatus.EXPIRED) {
      throw new PendingActionExpiredException(actionId);
    }
    return idempotentOrConflict(raced, resolution);
  }

  @Override
  public int expireDue(Instant now) {
    return jdbc.sql(
            "UPDATE ds_agent_pending_action SET status='expired',resolved_by='system',"
                + " resolved_at=:now,revision=revision+1,updated_at=:now"
                + " WHERE status='pending' AND expires_at<=:now")
        .param("now", SqlTimestamps.toParam(now))
        .update();
  }

  private AgentPendingAction idempotentOrConflict(
      AgentPendingAction current, PendingActionResolution resolution) {
    if (current.status() == resolution.status()
        && resolution.digest().equals(current.resolutionDigest())) {
      return current;
    }
    throw new PendingActionConflictException(current.id());
  }

  private void expireOne(AgentPendingAction action, Instant now) {
    jdbc.sql(
            "UPDATE ds_agent_pending_action SET status='expired',resolved_by='system',"
                + " resolved_at=:now,revision=revision+1,updated_at=:now"
                + " WHERE id=:id AND tenant_id=:tenant AND status='pending'"
                + " AND revision=:revision")
        .param("now", SqlTimestamps.toParam(now))
        .param("id", action.id())
        .param("tenant", action.tenantId())
        .param("revision", action.revision())
        .update();
  }
}
