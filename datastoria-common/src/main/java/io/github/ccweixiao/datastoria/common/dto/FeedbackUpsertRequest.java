package io.github.ccweixiao.datastoria.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Body of {@code POST /api/ai/chat/feedback/auto-explain} (A10). Validation lives in the controller
 * to match Node's plain-text error wording ({@code Invalid request format}).
 *
 * <p>{@code reasonCode} and {@code freeText} are normalised to {@code null} when {@code solved} is
 * {@code true}. When {@code solved} is {@code false}, {@code reasonCode} is required.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeedbackUpsertRequest(
    String source,
    String sessionId,
    String messageId,
    boolean solved,
    String reasonCode,
    String freeText,
    FeedbackPayload payload,
    Boolean recoveryActionTaken,
    Boolean ephemeral) {}
