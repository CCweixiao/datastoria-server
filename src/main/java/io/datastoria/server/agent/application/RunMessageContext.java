package io.datastoria.server.agent.application;

/** Run + message identity used by {@link RunLifecycleRecorder} to persist terminal state. */
public record RunMessageContext(
    String tenantId,
    String runId,
    String userId,
    String sessionId,
    String messageId,
    String modelConfigId) {}
