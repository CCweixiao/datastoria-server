package io.datastoria.server.dto;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

import io.datastoria.server.domain.ChatMessage;

/**
 * Wire shape for a chat message. {@code parts} and {@code metadata} are {@link JsonNode} so unknown
 * part types round-trip byte-for-byte (A08 wire fixture).
 *
 * <p>{@code metadata} serialises as JSON {@code null} when the row stored no metadata (Jackson
 * serialises a null {@link JsonNode} reference as {@code null}). When the row stored the JSON
 * literal {@code "{}"} or {@code "{\"usage\":...}"}, that object shape is preserved.
 */
public record ChatMessageDTO(
    String id,
    String role,
    JsonNode parts,
    JsonNode metadata,
    long sequence,
    Instant createdAt,
    Instant updatedAt) {

  public static ChatMessageDTO from(ChatMessage m, JsonNode parts, JsonNode metadata) {
    return new ChatMessageDTO(
        m.id(), m.role(), parts, metadata, m.sequence(), m.createdAt(), m.updatedAt());
  }
}
