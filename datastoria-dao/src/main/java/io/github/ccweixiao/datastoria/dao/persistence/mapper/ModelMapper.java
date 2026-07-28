package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.ModelEntity;

/**
 * Mapper for {@code ds_model}. Simple reads use {@link BaseMapper} + {@code LambdaQueryWrapper};
 * the insert and revision-guarded update/soft-delete are custom XML so the {@code active_key}
 * generated column is never written and {@code revision} is bumped under an optimistic-lock guard.
 */
public interface ModelMapper extends BaseMapper<ModelEntity> {

  int insertModel(ModelEntity entity);

  int updateModelCas(
      @Param("entity") ModelEntity entity, @Param("expectedRevision") long expectedRevision);

  int softDeleteModel(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);
}
