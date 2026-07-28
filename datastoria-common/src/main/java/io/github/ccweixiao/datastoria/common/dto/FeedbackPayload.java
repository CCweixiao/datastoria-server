package io.github.ccweixiao.datastoria.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Payload sub-document of {@link FeedbackUpsertRequest}. {@code queryId} is required. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FeedbackPayload(String queryId, String errorCode, String sql) {}
