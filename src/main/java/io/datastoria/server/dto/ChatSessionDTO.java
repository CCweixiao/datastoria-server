package io.datastoria.server.dto;

import java.time.Instant;

import io.datastoria.server.domain.ChatSession;

/**
 * Wire shape for a chat session in P3. Field names preserve Node compatibility:
 *
 * <ul>
 *   <li>{@code chatId} — session id (== row id of {@code ds_chat_session}).
 *   <li>{@code databaseId} — connection id.
 *   <li>{@code title} — nullable.
 *   <li>{@code createdAt}/{@code updatedAt} — ISO-8601 instants.
 * </ul>
 *
 * See {@code docs/api/p3-openapi-extensions.yaml} {@code ChatSessionDTO}.
 */
public record ChatSessionDTO(
    String chatId, String databaseId, String title, Instant createdAt, Instant updatedAt) {

  public static ChatSessionDTO from(ChatSession s) {
    return new ChatSessionDTO(
        s.id(), s.connectionId(), s.title(), s.createdAt(), s.updatedAt());
  }
}
