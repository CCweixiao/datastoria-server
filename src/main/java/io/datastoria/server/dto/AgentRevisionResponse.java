package io.datastoria.server.dto;

import java.time.Instant;

import io.datastoria.server.domain.AgentRevision;

public record AgentRevisionResponse(
    String id,
    String agentId,
    int version,
    String modelId,
    String systemPrompt,
    String promptChecksum,
    String runtimeConfigJson,
    String toolPolicyJson,
    String skillPolicyJson,
    String createdBy,
    Instant createdAt) {

  public static AgentRevisionResponse from(AgentRevision r) {
    return new AgentRevisionResponse(
        r.id(),
        r.agentId(),
        r.version(),
        r.modelId(),
        r.systemPrompt(),
        r.promptChecksum(),
        r.runtimeConfigJson(),
        r.toolPolicyJson(),
        r.skillPolicyJson(),
        r.createdBy(),
        r.createdAt());
  }
}
