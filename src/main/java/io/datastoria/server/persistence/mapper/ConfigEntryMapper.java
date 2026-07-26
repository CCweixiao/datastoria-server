package io.datastoria.server.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.ConfigEntryEntity;

/** Mapper for {@code ds_config_entry}; the layered upsert lives in the adapter. */
public interface ConfigEntryMapper extends BaseMapper<ConfigEntryEntity> {

  int insertConfigEntry(ConfigEntryEntity entity);

  ConfigEntryEntity findUserEntry(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("configKey") String configKey);

  int casUpdate(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("valueJson") String valueJson,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);

  List<ConfigEntryEntity> findEffective(
      @Param("tenantId") String tenantId, @Param("userId") String userId);

  int softDelete(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);
}
