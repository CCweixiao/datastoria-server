package io.github.ccweixiao.datastoria.common.domain.approval;

import java.util.List;

public record ApprovalDetail(
    ApprovalRequest request, List<ApprovalItem> items, List<ApprovalEvent> events) {

  public ApprovalDetail {
    items = List.copyOf(items);
    events = List.copyOf(events);
  }
}
