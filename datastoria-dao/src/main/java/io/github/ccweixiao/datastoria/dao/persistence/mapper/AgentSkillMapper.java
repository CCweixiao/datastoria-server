package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentSkillEntity;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentSkillResourceEntity;
import io.github.ccweixiao.datastoria.dao.persistence.entity.SkillResourceEntity;
import io.github.ccweixiao.datastoria.dao.persistence.entity.SkillRevisionEntity;

/**
 * Mapper for the skill bundle tables ({@code ds_agent_skill}, {@code ds_skill_revision}, {@code
 * ds_skill_resource}, {@code ds_agent_skill_resource}). The visibility reads JOIN {@code
 * ds_skill_revision}; the save-bundle orchestration and checksum computation live in the adapter.
 */
public interface AgentSkillMapper {

  List<AgentSkillEntity> findVisible(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("includeDraft") int includeDraft);

  AgentSkillEntity findById(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("id") String id,
      @Param("includeDraft") int includeDraft);

  AgentSkillEntity findRevision(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("id") String id,
      @Param("skillRevision") long skillRevision);

  AgentSkillEntity findRoot(@Param("tenantId") String tenantId, @Param("id") String id);

  int insertSkillRoot(AgentSkillEntity entity);

  int updateSkillRoot(AgentSkillEntity entity, @Param("published") int published);

  int insertSkillRevision(SkillRevisionEntity entity);

  int insertSkillResource(SkillResourceEntity entity);

  int deleteCompatResources(@Param("tenantId") String tenantId, @Param("skillId") String skillId);

  int insertCompatResource(AgentSkillResourceEntity entity);

  List<SkillResourceEntity> findResources(
      @Param("tenantId") String tenantId,
      @Param("skillId") String skillId,
      @Param("skillRevision") long skillRevision);

  int publish(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("id") String id,
      @Param("now") Instant now);

  int delete(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("id") String id,
      @Param("now") Instant now);
}
