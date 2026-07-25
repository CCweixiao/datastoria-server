package io.datastoria.server.dto;

import java.time.Instant;

/** Response for the recorded happy-path of A10. {@code recorded} is always {@code true}. */
public record FeedbackRecordedResponse(boolean recorded, Instant updatedAt, boolean solved) {

  public static FeedbackRecordedResponse of(Instant updatedAt, boolean solved) {
    return new FeedbackRecordedResponse(true, updatedAt, solved);
  }
}
