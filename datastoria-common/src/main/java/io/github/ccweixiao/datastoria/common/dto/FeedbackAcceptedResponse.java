package io.github.ccweixiao.datastoria.common.dto;

/** Response for the {@code 202} path of A10 — feedback was accepted but not stored. */
public record FeedbackAcceptedResponse(boolean recorded) {

  public static FeedbackAcceptedResponse notStored() {
    return new FeedbackAcceptedResponse(false);
  }
}
