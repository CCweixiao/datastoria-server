package io.datastoria.server.api.error;

/**
 * Thrown by {@code POST /api/ai/chat/feedback/auto-explain} when the referenced {@code messageId}
 * does not exist in the session. Deliberate divergence from Node (which returns HTTP 500);
 * authorised by ADR-0003. Mapped to {@code 404} RFC 9457 ProblemDetail with code {@code
 * FEEDBACK_TARGET_NOT_FOUND}.
 */
public class FeedbackTargetNotFoundException extends RuntimeException {

  public FeedbackTargetNotFoundException(String message) {
    super(message);
  }
}
