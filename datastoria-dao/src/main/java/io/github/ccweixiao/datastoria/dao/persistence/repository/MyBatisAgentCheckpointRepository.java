package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.github.ccweixiao.datastoria.common.agent.AgentCheckpoint;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentCheckpointEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.AgentCheckpointMapper;
import io.github.ccweixiao.datastoria.dao.repository.AgentCheckpointRepository;

/**
 * MyBatis-Plus adapter for {@code ds_agent_checkpoint}. Uses a dialect-neutral update-then-insert
 * upsert (no ON CONFLICT/ON DUPLICATE KEY): an existing row at {@code (tenantId, runId, sequence)}
 * is overwritten (preserving {@code created_at}); an insert race retries the update.
 */
@Repository
public class MyBatisAgentCheckpointRepository implements AgentCheckpointRepository {

  private final AgentCheckpointMapper mapper;

  public MyBatisAgentCheckpointRepository(AgentCheckpointMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(AgentCheckpoint c) {
    Instant now = Instant.now();
    AgentCheckpointEntity e = AgentCheckpointEntity.fromDomain(c);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    if (mapper.updateExisting(e, now) == 1) {
      return;
    }
    try {
      mapper.insertCheckpoint(e);
    } catch (RuntimeException insertFailure) {
      // Concurrent insert of the same (runId, sequence) may have won the race.
      if (mapper.updateExisting(e, Instant.now()) == 1) {
        return;
      }
      throw insertFailure;
    }
  }

  @Override
  public Optional<AgentCheckpoint> findLatest(String tenantId, String runId) {
    return Optional.ofNullable(mapper.findLatest(tenantId, runId))
        .map(AgentCheckpointEntity::toDomain);
  }

  @Override
  public Optional<AgentCheckpoint> findBySequence(String tenantId, String runId, long sequence) {
    return Optional.ofNullable(mapper.findBySequence(tenantId, runId, sequence))
        .map(AgentCheckpointEntity::toDomain);
  }

  @Override
  public List<AgentCheckpoint> findAllByRun(String tenantId, String runId) {
    return mapper.findAllByRun(tenantId, runId).stream()
        .map(AgentCheckpointEntity::toDomain)
        .toList();
  }
}
