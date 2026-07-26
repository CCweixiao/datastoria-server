package io.datastoria.server.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.AgentRevisionEntity;

/**
 * Mapper for {@code ds_agent_revision}. Reads JOIN {@code ds_agent_definition} to apply tenant
 * scoping and the soft-delete filter (this table has no {@code tenant_id}).
 */
public interface AgentRevisionMapper extends BaseMapper<AgentRevisionEntity> {

  int insertRevision(AgentRevisionEntity entity);

  AgentRevisionEntity findById(@Param("id") String id, @Param("tenantId") String tenantId);

  List<AgentRevisionEntity> findByAgentId(
      @Param("agentId") String agentId, @Param("tenantId") String tenantId);
}
