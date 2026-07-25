package io.datastoria.server.repository;

import java.util.List;

import io.datastoria.server.agent.domain.PersistedAgentFrame;

/** Exact emitted SSE frames used by Last-Event-ID replay. */
public interface AgentEventRepository {

  void append(PersistedAgentFrame frame);

  long maxSequence(String tenantId, String runId);

  List<PersistedAgentFrame> findAfter(String tenantId, String runId, long sequence);
}
