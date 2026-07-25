package io.datastoria.server.dto;

import java.time.Instant;

/**
 * Body of {@code POST /api/ai/sessions/{id}/share} (A09). {@code code} is an HS256 JWT; {@code url}
 * is path-relative; {@code expiresAt} defaults to 2100-01-01T00:00:00Z (Node compat).
 */
public record ShareResponse(String url, String code, Instant expiresAt) {}
