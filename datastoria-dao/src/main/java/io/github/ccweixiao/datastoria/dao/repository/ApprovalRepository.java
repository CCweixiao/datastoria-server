package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;
import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalEvent;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;

/** Tenant-scoped persistence boundary for approval aggregates and type definitions. */
public interface ApprovalRepository {

  void createTypeIfAbsent(ApprovalTypeDefinition definition);

  List<ApprovalTypeDefinition> findEnabledTypes(String tenantId, String connectionId);

  Optional<ApprovalTypeDefinition> findEnabledType(String tenantId, String typeKey);

  void createDraft(ApprovalRequest request, List<ApprovalItem> items, ApprovalEvent createdEvent);

  Optional<ApprovalDetail> findDetail(String tenantId, String requestId);

  List<ApprovalRequest> findRequests(
      String tenantId, String applicantUserId, ApprovalStatus status, int limit);

  boolean transition(
      String tenantId,
      String requestId,
      long expectedRevision,
      ApprovalStatus expectedStatus,
      ApprovalStatus targetStatus,
      String reviewerUserId,
      String reviewerDisplayName,
      String reviewComment,
      ApprovalEvent event);
}
