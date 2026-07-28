package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/**
 * Chat session row — the chat product's primary grouping for messages. Owned by a single (tenantId,
 * userId). Hard-deleted on {@code DELETE /api/ai/chat/sessions/{id}}; no soft-delete column. {@code
 * revision} supports optimistic locking on rename.
 */
public record ChatSession(
    String id,
    String tenantId,
    String userId,
    String connectionId,
    String title,
    long revision,
    Instant createdAt,
    Instant updatedAt) {}
