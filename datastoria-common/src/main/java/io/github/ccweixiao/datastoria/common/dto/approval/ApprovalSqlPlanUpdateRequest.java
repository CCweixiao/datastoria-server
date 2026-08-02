package io.github.ccweixiao.datastoria.common.dto.approval;

import java.util.List;

public record ApprovalSqlPlanUpdateRequest(long revision, List<Item> items) {
  public record Item(String id, String sqlText) {}
}
