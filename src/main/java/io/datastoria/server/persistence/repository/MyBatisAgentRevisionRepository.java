package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.AgentRevision;
import io.datastoria.server.persistence.entity.AgentRevisionEntity;
import io.datastoria.server.persistence.mapper.AgentRevisionMapper;
import io.datastoria.server.repository.AgentRevisionRepository;

/**
 * MyBatis-Plus adapter for {@code ds_agent_revision}. {@code save} does not re-read (mirrors JDBC).
 */
@Repository
public class MyBatisAgentRevisionRepository implements AgentRevisionRepository {

  private final AgentRevisionMapper mapper;

  public MyBatisAgentRevisionRepository(AgentRevisionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public AgentRevision save(AgentRevision r) {
    Instant createdAt = r.createdAt() != null ? r.createdAt() : Instant.now();
    AgentRevisionEntity e = AgentRevisionEntity.fromDomain(r);
    e.setCreatedAt(createdAt);
    mapper.insertRevision(e);
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
    return Optional.ofNullable(mapper.findById(id, tenantId)).map(AgentRevisionEntity::toDomain);
  }

  @Override
  public List<AgentRevision> findByAgentId(String agentId, String tenantId) {
    return mapper.findByAgentId(agentId, tenantId).stream()
        .map(AgentRevisionEntity::toDomain)
        .toList();
  }
}
