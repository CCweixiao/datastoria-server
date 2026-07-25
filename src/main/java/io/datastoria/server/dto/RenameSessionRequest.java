package io.datastoria.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Body of {@code PATCH /api/ai/chat/sessions/{id}} (A06). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenameSessionRequest(String title) {}
