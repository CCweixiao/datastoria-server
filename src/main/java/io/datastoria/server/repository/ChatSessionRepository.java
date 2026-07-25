package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.ChatSession;
import io.datastoria.server.repository.jdbc.SessionListCursor;

/**
 * Persistent chat-session access. Every method is scoped by {@code tenantId}; reads and writes
 * additionally scope by {@code userId} except the share-visitor lookup path which resolves the
 * owner from the JWT claims (the caller is responsible for passing the correct {@code userId}).
 */
public interface ChatSessionRepository {

  /**
   * Inserts a new session row. The caller MUST first invoke {@link #findById} to implement the
   * idempotent-on-{@code sessionId} semantics documented in A04; this method always INSERTs.
   */
  ChatSession save(ChatSession session);

  /**
   * Looks up a session by id under (tenantId, userId). Returns empty when the row is missing or
   * belongs to a different user.
   */
  Optional<ChatSession> findById(String id, String tenantId, String userId);

  /**
   * Keyset-paginated list ordered by {@code (updated_at DESC, id DESC)}. Pass {@code
   * connectionId = null} for an unfiltered list; pass a non-null cursor for the next page.
   */
  SessionPage findPage(
      String tenantId,
      String userId,
      String connectionId,
      SessionListCursor cursor,
      int limit);

  /** Renames the session and bumps {@code revision}; throws NotFound if missing. */
  ChatSession rename(String id, String tenantId, String userId, String title);

  /**
   * Hard-deletes the session row. Messages and feedback cascade via the V4 FK; share rows are
   * intentionally left as audit (no FK).
   */
  void delete(String id, String tenantId, String userId);

  /** Returns the complete list for a connection, used by import/reconciliation tooling. */
  List<ChatSession> findAllByConnection(String tenantId, String userId, String connectionId);
}
