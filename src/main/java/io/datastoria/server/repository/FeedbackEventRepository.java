package io.datastoria.server.repository;

import java.util.Optional;

import io.datastoria.server.domain.FeedbackEvent;

/**
 * Auto-explain feedback persistence. The natural key (tenantId, userId, source, sessionId,
 * messageId) is enforced by a UNIQUE constraint and upsert semantics; resubmission overwrites every
 * field.
 */
public interface FeedbackEventRepository {

  /**
   * Inserts or updates the feedback row keyed by (tenantId, userId, source, sessionId, messageId).
   * Returns the persisted row with refreshed timestamps.
   */
  FeedbackEvent upsert(FeedbackEvent event);

  /**
   * Looks up the existing feedback row for the natural key. Empty when no prior submission exists.
   */
  Optional<FeedbackEvent> find(
      String tenantId, String userId, String source, String sessionId, String messageId);
}
