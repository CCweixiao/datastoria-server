package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.github.ccweixiao.datastoria.common.agent.AgentPendingAction;
import io.github.ccweixiao.datastoria.common.agent.PendingActionConflictException;
import io.github.ccweixiao.datastoria.common.agent.PendingActionExpiredException;
import io.github.ccweixiao.datastoria.common.agent.PendingActionResolution;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentPendingActionEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.AgentPendingActionMapper;
import io.github.ccweixiao.datastoria.dao.repository.AgentPendingActionRepository;

/**
 * MyBatis-Plus adapter for {@code ds_agent_pending_action}. Resolution is a revision-guarded CAS;
 * terminal re-resolution is idempotent for the same decision/digest and conflicts otherwise.
 */
@Repository
public class MyBatisAgentPendingActionRepository implements AgentPendingActionRepository {

  private final AgentPendingActionMapper mapper;

  public MyBatisAgentPendingActionRepository(AgentPendingActionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public AgentPendingAction create(String userId, AgentPendingAction a) {
    AgentPendingActionEntity existing =
        mapper.findByToolCall(a.tenantId(), userId, a.runId(), a.toolCallId());
    if (existing != null) {
      AgentPendingAction row = existing.toDomain();
      if (row.id().equals(a.id())
          && row.actionType() == a.actionType()
          && row.requestJson().equals(a.requestJson())) {
        return row;
      }
      throw new PendingActionConflictException(row.id());
    }
    Instant now = Instant.now();
    AgentPendingActionEntity e = AgentPendingActionEntity.fromDomain(a);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    int inserted = mapper.createInsert(e, userId);
    if (inserted != 1) {
      throw new NotFoundException("AgentRun", a.runId());
    }
    return Optional.ofNullable(mapper.findById(a.tenantId(), userId, a.runId(), a.id()))
        .map(AgentPendingActionEntity::toDomain)
        .orElseThrow(() -> new NotFoundException("PendingAction", a.id()));
  }

  @Override
  public Optional<AgentPendingAction> find(
      String tenantId, String userId, String runId, String actionId) {
    return Optional.ofNullable(mapper.findById(tenantId, userId, runId, actionId))
        .map(AgentPendingActionEntity::toDomain);
  }

  @Override
  public Optional<AgentPendingAction> findByToolCall(
      String tenantId, String userId, String runId, String toolCallId) {
    return Optional.ofNullable(mapper.findByToolCall(tenantId, userId, runId, toolCallId))
        .map(AgentPendingActionEntity::toDomain);
  }

  @Override
  public List<AgentPendingAction> findPending(String tenantId, String userId, String runId) {
    return mapper.findPending(tenantId, userId, runId).stream()
        .map(AgentPendingActionEntity::toDomain)
        .toList();
  }

  @Override
  public AgentPendingAction resolve(
      String tenantId,
      String userId,
      String runId,
      String actionId,
      PendingActionResolution resolution) {
    AgentPendingAction current =
        Optional.ofNullable(mapper.findById(tenantId, userId, runId, actionId))
            .map(AgentPendingActionEntity::toDomain)
            .orElseThrow(() -> new NotFoundException("PendingAction", actionId));
    if (!resolution.status().isValidResolutionFor(current.actionType())) {
      throw new IllegalArgumentException("Invalid resolution status for action type");
    }
    if (current.status().isTerminal()) {
      return idempotentOrConflict(current, resolution);
    }
    if (!current.expiresAt().isAfter(resolution.resolvedAt())) {
      mapper.expireOne(current.id(), tenantId, current.revision(), resolution.resolvedAt());
      throw new PendingActionExpiredException(actionId);
    }
    int updated =
        mapper.resolveCas(
            tenantId,
            userId,
            runId,
            actionId,
            resolution.status().dbValue(),
            current.revision(),
            resolution);
    if (updated == 1) {
      return Optional.ofNullable(mapper.findById(tenantId, userId, runId, actionId))
          .map(AgentPendingActionEntity::toDomain)
          .orElseThrow();
    }
    AgentPendingAction raced =
        Optional.ofNullable(mapper.findById(tenantId, userId, runId, actionId))
            .map(AgentPendingActionEntity::toDomain)
            .orElseThrow();
    if (raced.status() == io.github.ccweixiao.datastoria.common.agent.PendingActionStatus.EXPIRED) {
      throw new PendingActionExpiredException(actionId);
    }
    return idempotentOrConflict(raced, resolution);
  }

  private AgentPendingAction idempotentOrConflict(
      AgentPendingAction current, PendingActionResolution resolution) {
    if (current.status() == resolution.status()
        && resolution.digest().equals(current.resolutionDigest())) {
      return current;
    }
    throw new PendingActionConflictException(current.id());
  }

  @Override
  public int expireDue(Instant now) {
    return mapper.expireDue(now);
  }
}
