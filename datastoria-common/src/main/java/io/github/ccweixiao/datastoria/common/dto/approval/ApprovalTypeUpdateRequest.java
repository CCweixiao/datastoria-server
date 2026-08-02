package io.github.ccweixiao.datastoria.common.dto.approval;

public record ApprovalTypeUpdateRequest(
    long revision,
    String nameEn,
    String nameZhCn,
    String descriptionEn,
    String descriptionZhCn,
    String generationRuleJson,
    boolean enabled) {}
