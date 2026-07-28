package io.github.ccweixiao.datastoria.common.dto;

public record CommandResponse(
    String name,
    String description,
    String skillId,
    boolean showInSqlEditorQuickAction,
    String template) {}
