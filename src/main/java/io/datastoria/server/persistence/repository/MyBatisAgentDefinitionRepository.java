package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.persistence.entity.AgentDefinitionEntity;
import io.datastoria.server.persistence.mapper.AgentDefinitionMapper;
import io.datastoria.server.repository.AgentDefinitionRepository;

/** MyBatis-Plus adapter for {@code ds_agent_definition}. */
@Repository
public class MyBatisAgentDefinitionRepository implements AgentDefinitionRepository {

  private final AgentDefinitionMapper mapper;

  public MyBatisAgentDefinitionRepository(AgentDefinitionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public AgentDefinition save(AgentDefinition d) {
    Instant now = Instant.now();
    AgentDefinitionEntity e = AgentDefinitionEntity.fromDomain(d);
    e.setRevision(0L);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    mapper.insertDefinition(e);
    return findById(d.id(), d.tenantId())
        .orElseThrow(() -> new NotFoundException("AgentDefinition", d.id()));
  }

  @Override
  public Optional<AgentDefinition> findById(String id, String tenantId) {
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<AgentDefinitionEntity>()
                    .eq(AgentDefinitionEntity::getId, id)
                    .eq(AgentDefinitionEntity::getTenantId, tenantId)
                    .isNull(AgentDefinitionEntity::getDeletedAt)))
        .map(AgentDefinitionEntity::toDomain);
  }

  @Override
  public List<AgentDefinition> findAll(String tenantId) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<AgentDefinitionEntity>()
                .eq(AgentDefinitionEntity::getTenantId, tenantId)
                .isNull(AgentDefinitionEntity::getDeletedAt))
        .stream()
        .map(AgentDefinitionEntity::toDomain)
        .toList();
  }

  @Override
  public AgentDefinition update(AgentDefinition d, long expectedRevision) {
    AgentDefinitionEntity e = AgentDefinitionEntity.fromDomain(d);
    e.setUpdatedAt(Instant.now());
    int affected = mapper.updateCas(e, expectedRevision);
    if (affected == 0) {
      if (findById(d.id(), d.tenantId()).isEmpty()) {
        throw new NotFoundException("AgentDefinition", d.id());
      }
      throw new RevisionConflictException("AgentDefinition", d.id(), expectedRevision, -1);
    }
    return findById(d.id(), d.tenantId()).orElseThrow();
  }

  @Override
  public void publish(String id, String tenantId, String revisionId, long expectedRevision) {
    int affected = mapper.publish(id, tenantId, revisionId, expectedRevision, Instant.now());
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("AgentDefinition", id);
      }
      throw new RevisionConflictException("AgentDefinition", id, expectedRevision, -1);
    }
  }

  @Override
  public void disable(String id, String tenantId, long expectedRevision) {
    int affected = mapper.disable(id, tenantId, expectedRevision, Instant.now());
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("AgentDefinition", id);
      }
      throw new RevisionConflictException("AgentDefinition", id, expectedRevision, -1);
    }
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int affected = mapper.softDelete(id, tenantId, expectedRevision, Instant.now());
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("AgentDefinition", id);
      }
      throw new RevisionConflictException("AgentDefinition", id, expectedRevision, -1);
    }
  }
}
