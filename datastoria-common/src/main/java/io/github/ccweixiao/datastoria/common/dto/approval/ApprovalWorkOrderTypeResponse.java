package io.github.ccweixiao.datastoria.common.dto.approval;

import java.util.List;

public record ApprovalWorkOrderTypeResponse(
    String typeKey,
    String nameI18nJson,
    String descriptionI18nJson,
    List<String> requiredIntentFields,
    String ruleSummary,
    long definitionRevision) {

  public ApprovalWorkOrderTypeResponse {
    requiredIntentFields = List.copyOf(requiredIntentFields);
  }
}
