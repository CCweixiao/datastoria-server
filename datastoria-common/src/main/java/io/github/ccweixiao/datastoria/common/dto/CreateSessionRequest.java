package io.github.ccweixiao.datastoria.common.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Body of {@code POST /api/ai/chat/sessions} (A04). Validation lives in the controller (inline
 * checks) so that the plain-text Node error format can be reproduced verbatim — bean-validation
 * messages would leak the framework's wording.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateSessionRequest(
    String connectionId, String sessionId, String title, List<AppUIMessage> messages) {}
