package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.RevisionConflictException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.ModelEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.ModelMapper;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

/** MyBatis-Plus adapter for {@code ds_model}. Behaviour mirrors the former JDBC repository. */
@Repository
public class MyBatisModelRepository implements ModelRepository {

  private final ModelMapper mapper;

  public MyBatisModelRepository(ModelMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Model save(Model m) {
    Instant now = Instant.now();
    ModelEntity e = ModelEntity.fromDomain(m);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    e.setRevision(0L);
    mapper.insertModel(e);
    return findById(m.id(), m.tenantId()).orElseThrow(() -> new NotFoundException("Model", m.id()));
  }

  @Override
  public Optional<Model> findById(String id, String tenantId) {
    ModelEntity e =
        mapper.selectOne(
            new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getId, id)
                .eq(ModelEntity::getTenantId, tenantId)
                .isNull(ModelEntity::getDeletedAt));
    return Optional.ofNullable(e).map(ModelEntity::toDomain);
  }

  @Override
  public List<Model> findAll(String tenantId) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, tenantId)
                .isNull(ModelEntity::getDeletedAt))
        .stream()
        .map(ModelEntity::toDomain)
        .toList();
  }

  @Override
  public List<Model> findEnabled(String tenantId) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, tenantId)
                .eq(ModelEntity::getEnabled, true)
                .isNull(ModelEntity::getDeletedAt))
        .stream()
        .map(ModelEntity::toDomain)
        .toList();
  }

  @Override
  public List<Model> findSystemModels(String tenantId) {
    return findByOwner(tenantId, null, false);
  }

  @Override
  public List<Model> findUserModels(String tenantId, String userId) {
    return findByOwner(tenantId, userId, false);
  }

  @Override
  public List<Model> findEnabledAccessible(String tenantId, String userId) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getTenantId, tenantId)
                .and(
                    scope ->
                        scope
                            .isNull(ModelEntity::getOwnerUserId)
                            .or()
                            .eq(ModelEntity::getOwnerUserId, userId))
                .eq(ModelEntity::getEnabled, true)
                .isNull(ModelEntity::getDeletedAt))
        .stream()
        .map(ModelEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<Model> findSystemById(String id, String tenantId) {
    return findOwnedById(id, tenantId, null);
  }

  @Override
  public Optional<Model> findUserById(String id, String tenantId, String userId) {
    return findOwnedById(id, tenantId, userId);
  }

  @Override
  public Optional<Model> findAccessibleById(String id, String tenantId, String userId) {
    ModelEntity entity =
        mapper.selectOne(
            new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getId, id)
                .eq(ModelEntity::getTenantId, tenantId)
                .and(
                    scope ->
                        scope
                            .isNull(ModelEntity::getOwnerUserId)
                            .or()
                            .eq(ModelEntity::getOwnerUserId, userId))
                .isNull(ModelEntity::getDeletedAt));
    return Optional.ofNullable(entity).map(ModelEntity::toDomain);
  }

  private Optional<Model> findOwnedById(String id, String tenantId, String userId) {
    LambdaQueryWrapper<ModelEntity> query =
        new LambdaQueryWrapper<ModelEntity>()
            .eq(ModelEntity::getId, id)
            .eq(ModelEntity::getTenantId, tenantId)
            .isNull(ModelEntity::getDeletedAt);
    if (userId == null) {
      query.isNull(ModelEntity::getOwnerUserId);
    } else {
      query.eq(ModelEntity::getOwnerUserId, userId);
    }
    return Optional.ofNullable(mapper.selectOne(query)).map(ModelEntity::toDomain);
  }

  private List<Model> findByOwner(String tenantId, String userId, boolean enabledOnly) {
    LambdaQueryWrapper<ModelEntity> query =
        new LambdaQueryWrapper<ModelEntity>()
            .eq(ModelEntity::getTenantId, tenantId)
            .isNull(ModelEntity::getDeletedAt);
    if (userId == null) {
      query.isNull(ModelEntity::getOwnerUserId);
    } else {
      query.eq(ModelEntity::getOwnerUserId, userId);
    }
    if (enabledOnly) {
      query.eq(ModelEntity::getEnabled, true);
    }
    return mapper.selectList(query).stream().map(ModelEntity::toDomain).toList();
  }

  @Override
  public boolean existsByProviderId(String providerId, String tenantId) {
    Long count =
        mapper.selectCount(
            new LambdaQueryWrapper<ModelEntity>()
                .eq(ModelEntity::getProviderId, providerId)
                .eq(ModelEntity::getTenantId, tenantId)
                .isNull(ModelEntity::getDeletedAt));
    return count != null && count > 0;
  }

  @Override
  public Model update(Model m, long expectedRevision) {
    ModelEntity e = ModelEntity.fromDomain(m);
    e.setUpdatedAt(Instant.now());
    int affected = mapper.updateModelCas(e, expectedRevision);
    if (affected == 0) {
      if (findById(m.id(), m.tenantId()).isEmpty()) {
        throw new NotFoundException("Model", m.id());
      }
      throw new RevisionConflictException("Model", m.id(), expectedRevision, -1);
    }
    return findById(m.id(), m.tenantId()).orElseThrow();
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int affected = mapper.softDeleteModel(id, tenantId, expectedRevision, Instant.now());
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("Model", id);
      }
      throw new RevisionConflictException("Model", id, expectedRevision, -1);
    }
  }
}
