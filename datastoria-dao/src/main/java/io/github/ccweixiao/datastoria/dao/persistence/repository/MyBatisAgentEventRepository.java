package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import io.github.ccweixiao.datastoria.common.agent.PersistedAgentFrame;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentEventEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.AgentEventMapper;
import io.github.ccweixiao.datastoria.dao.repository.AgentEventRepository;

/** MyBatis-Plus adapter for {@code ds_agent_event}. */
@Repository
public class MyBatisAgentEventRepository implements AgentEventRepository {

  private final AgentEventMapper mapper;

  public MyBatisAgentEventRepository(AgentEventMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void append(PersistedAgentFrame frame) {
    mapper.append(AgentEventEntity.fromDomain(frame));
  }

  @Override
  public long maxSequence(String tenantId, String runId) {
    Long max = mapper.maxSequence(tenantId, runId);
    return max == null ? 0L : max;
  }

  @Override
  public List<PersistedAgentFrame> findAfter(String tenantId, String runId, long sequence) {
    return mapper.findAfter(tenantId, runId, sequence).stream()
        .map(AgentEventEntity::toDomain)
        .toList();
  }
}
