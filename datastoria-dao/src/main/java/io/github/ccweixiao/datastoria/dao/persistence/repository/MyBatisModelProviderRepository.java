package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.RevisionConflictException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.ModelProviderEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.ModelProviderMapper;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderSecretRepository;

/**
 * MyBatis-Plus adapter for {@code ds_model_provider}. Also implements {@link
 * ModelProviderSecretRepository} for the privileged credential-rotation write previously exposed
 * only on the concrete JDBC repository.
 */
@Repository
public class MyBatisModelProviderRepository
    implements ModelProviderRepository, ModelProviderSecretRepository {

  private final ModelProviderMapper mapper;

  public MyBatisModelProviderRepository(ModelProviderMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ModelProvider save(ModelProvider p) {
    Instant now = Instant.now();
    ModelProviderEntity e = ModelProviderEntity.fromDomain(p);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    e.setRevision(0L);
    mapper.insertProvider(e);
    return findById(p.id(), p.tenantId())
        .orElseThrow(() -> new NotFoundException("ModelProvider", p.id()));
  }

  @Override
  public Optional<ModelProvider> findById(String id, String tenantId) {
    ModelProviderEntity e =
        mapper.selectOne(
            new LambdaQueryWrapper<ModelProviderEntity>()
                .eq(ModelProviderEntity::getId, id)
                .eq(ModelProviderEntity::getTenantId, tenantId)
                .isNull(ModelProviderEntity::getDeletedAt));
    return Optional.ofNullable(e).map(ModelProviderEntity::toDomain);
  }

  @Override
  public List<ModelProvider> findAll(String tenantId) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelProviderEntity>()
                .eq(ModelProviderEntity::getTenantId, tenantId)
                .isNull(ModelProviderEntity::getDeletedAt))
        .stream()
        .map(ModelProviderEntity::toDomain)
        .toList();
  }

  @Override
  public ModelProvider update(ModelProvider p, long expectedRevision) {
    ModelProviderEntity e = ModelProviderEntity.fromDomain(p);
    e.setUpdatedAt(Instant.now());
    int affected = mapper.updateProviderCas(e, expectedRevision);
    if (affected == 0) {
      if (findById(p.id(), p.tenantId()).isEmpty()) {
        throw new NotFoundException("ModelProvider", p.id());
      }
      throw new RevisionConflictException("ModelProvider", p.id(), expectedRevision, -1);
    }
    return findById(p.id(), p.tenantId()).orElseThrow();
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int affected = mapper.softDeleteProvider(id, tenantId, expectedRevision, Instant.now());
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("ModelProvider", id);
      }
      throw new RevisionConflictException("ModelProvider", id, expectedRevision, -1);
    }
  }

  @Override
  public void updateSecretId(String id, String tenantId, String secretId) {
    int affected = mapper.updateSecretId(id, tenantId, secretId, Instant.now());
    if (affected == 0) {
      throw new NotFoundException("ModelProvider", id);
    }
  }
}
