package io.datastoria.server.dto;

import java.util.List;

import io.datastoria.server.domain.ChatSession;

/** Wire shape for {@code GET /api/ai/chat/sessions}. {@code nextCursor} is {@code null} at EOS. */
public record SessionPageDTO(List<ChatSessionDTO> sessions, String nextCursor) {

  public static SessionPageDTO from(List<ChatSession> rows, String nextCursor) {
    List<ChatSessionDTO> dtos =
        rows.stream().map(ChatSessionDTO::from).collect(java.util.stream.Collectors.toList());
    return new SessionPageDTO(dtos, nextCursor);
  }
}
