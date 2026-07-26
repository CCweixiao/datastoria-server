package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.datastoria.server.agent.domain.AgentRun;
import io.datastoria.server.agent.domain.AgentRunStatus;
import io.datastoria.server.agent.domain.IllegalRunTransitionException;
import io.datastoria.server.agent.domain.RunTransition;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.persistence.entity.AgentRunEntity;
import io.datastoria.server.persistence.mapper.AgentRunMapper;
import io.datastoria.server.repository.AgentRunRepository;

/**
 * MyBatis-Plus adapter for {@code ds_agent_run}. Transitions use a conditional {@code revision}
 * optimistic-lock UPDATE; a transition that loses a race re-reads the row and either treats the
 * same target as idempotent or rejects the now-unreachable transition. Terminal states can never be
 * overwritten.
 */
@Repository
public class MyBatisAgentRunRepository implements AgentRunRepository {

  private final AgentRunMapper mapper;

  public MyBatisAgentRunRepository(AgentRunMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public AgentRun create(AgentRun r) {
    Instant now = r.createdAt() != null ? r.createdAt() : Instant.now();
    Instant updated = r.updatedAt() != null ? r.updatedAt() : now;
    AgentRunEntity e = AgentRunEntity.fromDomain(r);
    e.setCreatedAt(now);
    e.setUpdatedAt(updated);
    mapper.insertRun(e);
    return find(r.tenantId(), r.id()).orElseThrow(() -> new NotFoundException("AgentRun", r.id()));
  }

  @Override
  public Optional<AgentRun> find(String tenantId, String runId) {
    return Optional.ofNullable(mapper.findByTenantAndId(tenantId, runId))
        .map(AgentRunEntity::toDomain);
  }

  @Override
  public Optional<AgentRun> findByIdempotencyKey(
      String tenantId, String userId, String idempotencyKey) {
    return Optional.ofNullable(mapper.findByIdempotencyKey(tenantId, userId, idempotencyKey))
        .map(AgentRunEntity::toDomain);
  }

  @Override
  public List<AgentRun> findBySession(String tenantId, String sessionId) {
    return mapper.findBySession(tenantId, sessionId).stream()
        .map(AgentRunEntity::toDomain)
        .toList();
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
    AgentRunEntity row = mapper.findByIdInternal(runId);
    if (row == null) {
      return false;
    }
    try {
      return doTransition(
          row.toDomain(), AgentRunStatus.CANCELLED, RunTransition.cancelling(cancelledAt));
    } catch (IllegalRunTransitionException e) {
      // Late cancel after a non-cancellable terminal state is a safe no-op.
      return false;
    }
  }

  private boolean doTransition(AgentRun run, AgentRunStatus to, RunTransition payload) {
    if (run.status() == to) {
      return true;
    }
    if (!run.status().canTransitionTo(to)) {
      throw new IllegalRunTransitionException(run.id(), run.status(), to);
    }
    int updated =
        mapper.transition(
            run.tenantId(), run.id(), run.revision(), to.dbValue(), Instant.now(), payload);
    if (updated == 1) {
      return true;
    }
    AgentRunEntity current = mapper.findByIdInternal(run.id());
    AgentRunStatus currentStatus = current == null ? run.status() : current.toDomain().status();
    if (currentStatus == to) {
      return true;
    }
    throw new IllegalRunTransitionException(run.id(), currentStatus, to);
  }
}
