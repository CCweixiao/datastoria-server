package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;
import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.AgentRevision;

/** Immutable-insert repository for {@link AgentRevision}. */
public interface AgentRevisionRepository {

  AgentRevision save(AgentRevision revision);

  Optional<AgentRevision> findById(String id, String tenantId);

  List<AgentRevision> findByAgentId(String agentId, String tenantId);
}
