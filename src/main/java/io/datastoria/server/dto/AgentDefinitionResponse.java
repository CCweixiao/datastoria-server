package io.datastoria.server.dto;

import java.time.Instant;
import java.util.List;

import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.domain.AgentRevision;

public record AgentDefinitionResponse(
    String id,
    String agentKey,
    String name,
    String description,
    String status,
    String publishedRevisionId,
    long revision,
    Instant createdAt,
    Instant updatedAt,
    List<AgentRevisionResponse> revisions) {

  public static AgentDefinitionResponse from(AgentDefinition d, List<AgentRevision> revisions) {
    return new AgentDefinitionResponse(
        d.id(),
        d.agentKey(),
        d.name(),
        d.description(),
        d.status(),
        d.publishedRevisionId(),
        d.revision(),
        d.createdAt(),
        d.updatedAt(),
        revisions.stream().map(AgentRevisionResponse::from).toList());
  }
}
