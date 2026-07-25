package io.datastoria.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Initial message supplied to {@code POST /api/ai/chat/sessions} (A04). {@code parts} is an open
 * JSON tree so callers can pass any part shape; the server persists the JSON verbatim.
 *
 * <p>Unknown fields on the message or on individual parts are intentionally ignored at the binding
 * layer (Node does the same); the {@code additionalProperties: true} clause of the OpenAPI {@code
 * AppUIMessage}/{@code MessagePart} schemas documents this.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppUIMessage(String id, String role, JsonNode parts) {}
