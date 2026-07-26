package io.datastoria.server.persistence.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.AgentDefinitionEntity;

/**
 * Mapper for {@code ds_agent_definition}. Publish/disable/soft-delete are revision-guarded CAS
 * writes.
 */
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinitionEntity> {

  int insertDefinition(AgentDefinitionEntity entity);

  int updateCas(
      @Param("entity") AgentDefinitionEntity entity,
      @Param("expectedRevision") long expectedRevision);

  int publish(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("revisionId") String revisionId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);

  int disable(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);

  int softDelete(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("expectedRevision") long expectedRevision,
      @Param("now") Instant now);
}
