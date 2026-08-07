package io.github.ccweixiao.datastoria.common.dto.approval;

import java.util.List;

public record ApprovalWorkOrderTypeResponse(
    String typeKey,
    String nameI18nJson,
    String descriptionI18nJson,
    List<String> requiredIntentFields,
    List<IntentField> intentSchema,
    String ruleSummary,
    long definitionRevision) {

  /**
   * Declarative intent field (V2 intent_schema): tells the Agent what to pass for each type, so it
   * does not call {@code prepare} blind. {@code source} follows the V2 taxonomy — {@code
   * user-provided} (Agent cannot know, must ask), {@code agent-derived} (Agent can infer), {@code
   * schema-verified} (server checks), {@code mixed}.
   */
  public record IntentField(String name, String type, boolean required, String source) {}

  public ApprovalWorkOrderTypeResponse {
    requiredIntentFields = List.copyOf(requiredIntentFields);
    intentSchema = List.copyOf(intentSchema);
  }
}
