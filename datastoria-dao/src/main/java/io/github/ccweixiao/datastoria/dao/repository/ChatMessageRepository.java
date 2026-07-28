package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;
import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.ChatMessage;

/**
 * Chat message persistence. Messages are scoped by (tenantId, sessionId). Upsert semantics on
 * (tenantId, sessionId, id) support the idempotent A04 create flow.
 */
public interface ChatMessageRepository {

  /** Inserts or updates by (tenantId, sessionId, id). Returns the persisted row. */
  ChatMessage save(ChatMessage message);

  /** Looks up a single message by id within a session. */
  Optional<ChatMessage> findById(String id, String tenantId, String sessionId);

  /**
   * Lists messages for a session ordered by {@code sequence ASC}. Returns an empty list when the
   * session exists but has no messages.
   */
  List<ChatMessage> findBySession(String sessionId, String tenantId);

  /**
   * Whether any message with the given id exists in the session under the caller's identity. Used
   * by the feedback path to translate "target message missing" into HTTP 404 (ADR-0003).
   */
  boolean exists(String tenantId, String userId, String sessionId, String messageId);
}
