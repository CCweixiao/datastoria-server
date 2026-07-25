package io.datastoria.server.agent.domain;

/** Immutable Skill revision selected when an agent run is created. */
public record AgentRunSkillPin(
    String tenantId, String runId, String skillId, long skillRevision, String contentChecksum) {}
