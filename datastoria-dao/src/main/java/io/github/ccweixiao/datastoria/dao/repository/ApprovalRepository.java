package io.github.ccweixiao.datastoria.dao.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalEvent;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalNodeExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;

/** Tenant-scoped persistence boundary for approval aggregates and type definitions. */
public interface ApprovalRepository {

  void createTypeIfAbsent(ApprovalTypeDefinition definition);

  List<ApprovalTypeDefinition> findEnabledTypes(String tenantId, String connectionId);

  Optional<ApprovalTypeDefinition> findEnabledType(String tenantId, String typeKey);

  List<ApprovalTypeDefinition> findTypes(String tenantId);

  Optional<ApprovalTypeDefinition> findType(String tenantId, String typeKey);

  boolean updateType(
      String tenantId,
      String typeKey,
      long expectedRevision,
      String nameI18nJson,
      String descriptionI18nJson,
      String generationRuleJson,
      String status,
      String checksum,
      String actorUserId);

  void createDraft(
      ApprovalRequest request,
      List<ApprovalItem> items,
      String idempotencyKey,
      ApprovalEvent createdEvent);

  Optional<ApprovalDetail> findDetailByIdempotencyKey(
      String tenantId, String applicantUserId, String idempotencyKey);

  boolean updateDraft(
      ApprovalRequest request,
      long expectedRevision,
      List<ApprovalItem> items,
      String idempotencyKey,
      ApprovalEvent updatedEvent);

  Optional<ApprovalDetail> findDetail(String tenantId, String requestId);

  List<ApprovalRequest> findRequests(
      String tenantId,
      String visibleApplicantUserId,
      List<ApprovalStatus> statuses,
      String workOrderTypeKey,
      String applicant,
      String keyword,
      Instant createdFrom,
      Instant createdTo,
      int offset,
      int limit);

  long countRequests(
      String tenantId,
      String visibleApplicantUserId,
      List<ApprovalStatus> statuses,
      String workOrderTypeKey,
      String applicant,
      String keyword,
      Instant createdFrom,
      Instant createdTo);

  boolean updateSqlPlan(
      ApprovalRequest request,
      long expectedRevision,
      ApprovalStatus expectedStatus,
      List<ApprovalItem> items,
      ApprovalEvent event);

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

  boolean submitWithResourceClaims(
      String tenantId,
      String requestId,
      long expectedRevision,
      List<String> resourceKeys,
      String actorUserId,
      ApprovalEvent event);

  int beginExecution(
      String tenantId,
      String requestId,
      long expectedRevision,
      String actorUserId,
      ApprovalEvent event);

  String createExecution(
      String tenantId, String requestId, String itemId, int attemptNo, int ordinal, String queryId);

  String createNodeExecution(
      String tenantId, String executionId, String nodeKey, String host, Integer port);

  void finishExecution(
      String tenantId,
      String executionId,
      boolean succeeded,
      long durationMs,
      String errorCode,
      String safeMessage);

  void finishNodeExecution(
      String tenantId,
      String nodeExecutionId,
      boolean succeeded,
      long durationMs,
      String errorCode,
      String safeMessage);

  List<ApprovalExecution> findExecutions(String tenantId, String requestId);

  List<ApprovalNodeExecution> findNodeExecutions(
      String tenantId, String executionId, String status, int offset, int limit);

  void finishRequestExecution(
      String tenantId,
      String requestId,
      long expectedRevision,
      ApprovalStatus expectedStatus,
      ApprovalStatus targetStatus,
      String actorUserId,
      ApprovalEvent event);

  int retryExecution(
      String tenantId,
      String requestId,
      long expectedRevision,
      String actorUserId,
      ApprovalEvent event);

  Set<String> findSucceededItemIds(String tenantId, String requestId);

  void createSkippedExecution(
      String tenantId, String requestId, String itemId, int attemptNo, int ordinal);

  List<ApprovalRequest> findClaimableQueuedRequests(int limit);

  int claimQueued(
      String tenantId,
      String requestId,
      long expectedRevision,
      java.time.Instant leaseUntil,
      String actorUserId,
      ApprovalEvent event);
}
