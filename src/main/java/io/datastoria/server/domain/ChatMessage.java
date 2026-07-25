package io.datastoria.server.domain;

import java.time.Instant;

/**
 * Single chat message. {@code partsJson}/{@code metadataJson} are stored as raw JSON strings; the
 * DTO layer deserialises them to {@code JsonNode} so unknown part types round-trip byte-for-byte
 * (see ADR for A08 wire fixture).
 *
 * <p>{@code sequence} is unique per session and assigned by the caller (Node behaviour preserved).
 * Messages are ordered strictly by {@code sequence ASC} on read.
 */
public record ChatMessage(
    String id,
    String tenantId,
    String sessionId,
    String userId,
    String role,
    String partsJson,
    String metadataJson,
    long sequence,
    Instant createdAt,
    Instant updatedAt) {}
