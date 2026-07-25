package io.datastoria.server.dto;

public record CommandResponse(
    String name,
    String description,
    String skillId,
    boolean showInSqlEditorQuickAction,
    String template) {}
