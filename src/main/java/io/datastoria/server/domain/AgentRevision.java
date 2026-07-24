package io.datastoria.server.domain;

import java.time.Instant;

/**
 * An immutable agent revision. Once created, its fields never change. Publishing atomically sets
 * {@link AgentDefinition#publishedRevisionId()} to a revision id; existing runs always reference
 * the revision captured at run-creation time.
 */
public record AgentRevision(
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
    Instant createdAt) {}
