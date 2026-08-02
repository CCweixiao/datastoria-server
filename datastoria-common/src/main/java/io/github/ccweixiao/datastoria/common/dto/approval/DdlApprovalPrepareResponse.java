package io.github.ccweixiao.datastoria.common.dto.approval;

import java.util.List;

public record DdlApprovalPrepareResponse(
    String draftId,
    String requestNo,
    long revision,
    String contentDigest,
    List<PreparedStatement> orderedItems,
    List<String> appliedRuleSummary,
    boolean submittable) {

  public record PreparedStatement(
      int ordinal, String operationKind, String sql, String riskLevel, List<String> warnings) {

    public PreparedStatement {
      warnings = List.copyOf(warnings);
    }
  }

  public DdlApprovalPrepareResponse {
    orderedItems = List.copyOf(orderedItems);
    appliedRuleSummary = List.copyOf(appliedRuleSummary);
  }
}
