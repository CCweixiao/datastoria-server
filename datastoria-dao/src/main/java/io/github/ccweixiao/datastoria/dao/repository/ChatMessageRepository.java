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

  /**
   * Idempotent batch insert of a session's initial messages.
   *
   * <p>Messages whose id already exists in the session are skipped (first-write-wins — their
   * sequence and content are left untouched); new messages are appended with sequence {@code
   * max(existing) + 1}, {@code + 2}, …. Sequences are therefore never restarted from 1, which is
   * what keeps an idempotent re-create of an existing session from colliding on {@code
   * uk_message_session_sequence}.
   *
   * <p>A concurrent append that nonetheless takes the same sequence slot is recovered by
   * recomputing the session max and retrying the insert, so this call does not surface a
   * duplicate-key error to the caller.
   */
  void saveInitialMessages(
      String tenantId, String sessionId, String userId, List<InitialMessage> messages);

  /** Initial-message input carrying already-sanitised JSON payloads. */
  record InitialMessage(String id, String role, String partsJson, String metadataJson) {}

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
