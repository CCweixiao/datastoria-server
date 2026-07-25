package io.datastoria.server.domain;

import java.time.Instant;

/**
 * Auto-explain feedback row. Natural upsert key is {@code (tenantId, userId, source, sessionId,
 * messageId)}; resubmission overwrites every field.
 *
 * <p>When {@code solved = true}, the service normalises {@code reasonCode} and {@code freeText} to
 * {@code null} before persisting (matches Node behaviour).
 */
public record FeedbackEvent(
    String id,
    String tenantId,
    String userId,
    String source,
    String sessionId,
    String messageId,
    boolean solved,
    String reasonCode,
    String payloadJson,
    String freeText,
    boolean recoveryActionTaken,
    Instant createdAt,
    Instant updatedAt) {}
