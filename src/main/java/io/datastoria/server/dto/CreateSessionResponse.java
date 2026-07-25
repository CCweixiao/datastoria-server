package io.datastoria.server.dto;

/**
 * Wrapper for the {@code POST /api/ai/chat/sessions} response (A04). Node wraps the session in a
 * {@code { session: ... }} envelope; Java preserves the shape exactly even though it's redundant.
 */
public record CreateSessionResponse(ChatSessionDTO session) {

  public static CreateSessionResponse of(ChatSessionDTO session) {
    return new CreateSessionResponse(session);
  }
}
