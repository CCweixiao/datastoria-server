package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.ClickHouseConnectionEntity;

/** Mapper for {@code ds_clickhouse_connection} (BLOB password columns, CAS update/soft-delete). */
public interface ClickHouseConnectionMapper extends BaseMapper<ClickHouseConnectionEntity> {

  int insertConnection(ClickHouseConnectionEntity entity);

  ClickHouseConnectionEntity findById(@Param("id") String id, @Param("tenantId") String tenantId);

  List<ClickHouseConnectionEntity> findAll(@Param("tenantId") String tenantId);

  int updateCas(
      @Param("entity") ClickHouseConnectionEntity entity,
      @Param("expectedRevision") long expectedRevision);

  int softDelete(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);
}
