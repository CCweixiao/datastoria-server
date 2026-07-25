package io.datastoria.server.dto;

import java.util.List;

public record SkillDetailResponse(
    String id,
    String name,
    String description,
    String source,
    String status,
    String state,
    String scope,
    String version,
    String author,
    String summary,
    boolean hasResources,
    boolean disableSlashCommand,
    boolean showInSqlEditorQuickAction,
    List<String> requiredTools,
    boolean canEdit,
    String content,
    List<String> resourcePaths) {}
