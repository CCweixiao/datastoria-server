package io.datastoria.server.agent.domain;

import java.time.Instant;

/** One exact AI SDK SSE frame retained for idempotent run replay. */
public record PersistedAgentFrame(
    String id, String tenantId, String runId, long sequence, String frameText, Instant createdAt) {}
