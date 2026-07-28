package io.github.ccweixiao.datastoria.dao.repository;

import io.github.ccweixiao.datastoria.common.domain.ChatSession;

/**
 * Paginated slice of sessions returned by {@link ChatSessionRepository#findPage}. {@code
 * nextCursor} is opaque to the client (see {@code SessionListCursor} for the wire format); {@code
 * null} indicates the end of the result set.
 */
public record SessionPage(java.util.List<ChatSession> sessions, String nextCursor) {}
