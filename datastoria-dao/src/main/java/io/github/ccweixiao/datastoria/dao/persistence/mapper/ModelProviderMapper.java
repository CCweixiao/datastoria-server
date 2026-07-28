package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.ModelProviderEntity;

/**
 * Mapper for {@code ds_model_provider}. {@link #updateSecretId} is the privileged
 * credential-rotation write: it sets {@code secret_id} + {@code updated_at} without bumping {@code
 * revision}.
 */
public interface ModelProviderMapper extends BaseMapper<ModelProviderEntity> {

  int insertProvider(ModelProviderEntity entity);

  int updateProviderCas(
      @Param("entity") ModelProviderEntity entity,
      @Param("expectedRevision") long expectedRevision);

  int softDeleteProvider(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);

  int updateSecretId(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("secretId") String secretId,
      @Param("now") Instant now);
}
