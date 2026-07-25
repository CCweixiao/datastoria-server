package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.AgentSkill;
import io.datastoria.server.domain.AgentSkillResource;

public interface AgentSkillRepository {
  List<AgentSkill> findVisible(String tenantId, String userId, boolean includeDraft);

  Optional<AgentSkill> findById(String tenantId, String userId, String id, boolean includeDraft);

  AgentSkill saveBundle(AgentSkill skill, List<AgentSkillResource> resources);

  List<AgentSkillResource> findResources(String tenantId, String skillId, long skillRevision);

  void publish(String tenantId, String userId, String id);

  void delete(String tenantId, String userId, String id);
}
