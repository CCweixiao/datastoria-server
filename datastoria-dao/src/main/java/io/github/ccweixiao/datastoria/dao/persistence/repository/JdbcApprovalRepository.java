package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalEvent;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalNodeExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.dao.repository.ApprovalRepository;

@Repository
public class JdbcApprovalRepository implements ApprovalRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public JdbcApprovalRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void createTypeIfAbsent(ApprovalTypeDefinition d) {
    jdbc.update(
        """
        INSERT IGNORE INTO ds_approval_type_definition (
          id, tenant_id, type_key, handler_key, name_i18n_json, description_i18n_json,
          generator_key, allowed_operation_kinds_json, generation_rule_json,
          applicable_connections_json, risk_policy_json, status, definition_revision,
          checksum, created_by, updated_by, enabled_by, created_at, updated_at, enabled_at)
        VALUES (
          :id, :tenantId, :typeKey, :handlerKey, :nameI18nJson, :descriptionI18nJson,
          :generatorKey, :allowedOperationKindsJson, :generationRuleJson,
          :applicableConnectionsJson, :riskPolicyJson, :status, :definitionRevision,
          :checksum, :createdBy, :updatedBy, :enabledBy, :createdAt, :updatedAt, :enabledAt)
        """,
        typeParameters(d));
  }

  @Override
  public List<ApprovalTypeDefinition> findEnabledTypes(String tenantId, String connectionId) {
    return jdbc.query(
        """
        SELECT * FROM ds_approval_type_definition
        WHERE tenant_id = :tenantId AND status = 'ENABLED'
          AND (applicable_connections_json IS NULL
            OR JSON_CONTAINS(applicable_connections_json, JSON_QUOTE(:connectionId)))
        ORDER BY type_key
        """,
        Map.of("tenantId", tenantId, "connectionId", connectionId),
        JdbcApprovalRepository::mapType);
  }

  @Override
  public Optional<ApprovalTypeDefinition> findEnabledType(String tenantId, String typeKey) {
    List<ApprovalTypeDefinition> rows =
        jdbc.query(
            """
            SELECT * FROM ds_approval_type_definition
            WHERE tenant_id = :tenantId AND type_key = :typeKey AND status = 'ENABLED'
            """,
            Map.of("tenantId", tenantId, "typeKey", typeKey),
            JdbcApprovalRepository::mapType);
    return rows.stream().findFirst();
  }

  @Override
  public List<ApprovalTypeDefinition> findTypes(String tenantId) {
    return jdbc.query(
        "SELECT * FROM ds_approval_type_definition WHERE tenant_id = :tenantId ORDER BY type_key",
        Map.of("tenantId", tenantId),
        JdbcApprovalRepository::mapType);
  }

  @Override
  public Optional<ApprovalTypeDefinition> findType(String tenantId, String typeKey) {
    return jdbc
        .query(
            "SELECT * FROM ds_approval_type_definition WHERE tenant_id = :tenantId AND type_key = :typeKey",
            Map.of("tenantId", tenantId, "typeKey", typeKey),
            JdbcApprovalRepository::mapType)
        .stream()
        .findFirst();
  }

  @Override
  public boolean updateType(
      String tenantId,
      String typeKey,
      long expectedRevision,
      String nameI18nJson,
      String descriptionI18nJson,
      String generationRuleJson,
      String status,
      String checksum,
      String actorUserId) {
    Instant now = Instant.now();
    return jdbc.update(
            """
            UPDATE ds_approval_type_definition
            SET name_i18n_json = :nameI18nJson, description_i18n_json = :descriptionI18nJson,
              generation_rule_json = :generationRuleJson, status = :status, checksum = :checksum,
              definition_revision = definition_revision + 1, updated_by = :actorUserId,
              enabled_by = CASE WHEN :status = 'ENABLED' THEN :actorUserId ELSE enabled_by END,
              enabled_at = CASE WHEN :status = 'ENABLED' THEN :now ELSE enabled_at END,
              updated_at = :now
            WHERE tenant_id = :tenantId AND type_key = :typeKey
              AND definition_revision = :expectedRevision
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("typeKey", typeKey)
                .addValue("expectedRevision", expectedRevision)
                .addValue("nameI18nJson", nameI18nJson)
                .addValue("descriptionI18nJson", descriptionI18nJson)
                .addValue("generationRuleJson", generationRuleJson)
                .addValue("status", status)
                .addValue("checksum", checksum)
                .addValue("actorUserId", actorUserId)
                .addValue("now", timestamp(now)))
        == 1;
  }

  @Override
  @Transactional
  public void createDraft(
      ApprovalRequest request,
      List<ApprovalItem> items,
      String idempotencyKey,
      ApprovalEvent createdEvent) {
    jdbc.update(
        """
        INSERT INTO ds_approval_request (
          id, tenant_id, request_no, type, work_order_type_key, work_order_type_revision,
          type_definition_checksum, title, summary, applicant_user_id, applicant_display_name,
          source_session_id, source_run_id, connection_id, connection_name, status,
          content_json, content_version, content_digest, execution_mode, execution_attempt,
          idempotency_key, revision, created_at, updated_at,
          plan_version, plan_hash, env_snapshot_json, policy_version_ref)
        VALUES (
          :id, :tenantId, :requestNo, 'CLICKHOUSE_DDL', :workOrderTypeKey,
          :workOrderTypeRevision, :typeDefinitionChecksum, :title, :summary,
          :applicantUserId, :applicantDisplayName, :sourceSessionId, :sourceRunId,
          :connectionId, :connectionName, :status, :contentJson, :contentVersion,
          :contentDigest, :executionMode, :executionAttempt, :idempotencyKey,
          :revision, :createdAt, :updatedAt,
          :planVersion, :planHash, :envSnapshotJson, :policyVersionRef)
        """,
        requestParameters(request).addValue("idempotencyKey", idempotencyKey));
    for (ApprovalItem item : items) {
      insertItem(item);
    }
    insertEvent(createdEvent);
  }

  @Override
  public Optional<ApprovalDetail> findDetailByIdempotencyKey(
      String tenantId, String applicantUserId, String idempotencyKey) {
    List<String> ids =
        jdbc.queryForList(
            """
            SELECT id FROM ds_approval_request
            WHERE tenant_id = :tenantId AND applicant_user_id = :applicantUserId
              AND idempotency_key = :idempotencyKey AND deleted_at IS NULL
            """,
            Map.of(
                "tenantId", tenantId,
                "applicantUserId", applicantUserId,
                "idempotencyKey", idempotencyKey),
            String.class);
    return ids.isEmpty() ? Optional.empty() : findDetail(tenantId, ids.get(0));
  }

  @Override
  @Transactional
  public boolean updateDraft(
      ApprovalRequest request,
      long expectedRevision,
      List<ApprovalItem> items,
      String idempotencyKey,
      ApprovalEvent updatedEvent) {
    Instant now = Instant.now();
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET work_order_type_key = :workOrderTypeKey,
              work_order_type_revision = :workOrderTypeRevision,
              type_definition_checksum = :typeDefinitionChecksum,
              title = :title, summary = :summary,
              source_session_id = :sourceSessionId, source_run_id = :sourceRunId,
              connection_id = :connectionId, connection_name = :connectionName,
              content_json = :contentJson, content_version = content_version + 1,
              content_digest = :contentDigest, idempotency_key = :idempotencyKey,
              plan_version = :planVersion, plan_hash = :planHash,
              env_snapshot_json = :envSnapshotJson, policy_version_ref = :policyVersionRef,
              revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :id
              AND applicant_user_id = :applicantUserId
              AND status = 'DRAFT' AND revision = :expectedRevision AND deleted_at IS NULL
            """,
            requestParameters(request)
                .addValue("expectedRevision", expectedRevision)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("now", timestamp(now)));
    if (affected != 1) return false;
    jdbc.update(
        "DELETE FROM ds_approval_item WHERE tenant_id = :tenantId AND request_id = :requestId",
        Map.of("tenantId", request.tenantId(), "requestId", request.id()));
    items.forEach(this::insertItem);
    insertEvent(updatedEvent);
    return true;
  }

  @Override
  public Optional<ApprovalDetail> findDetail(String tenantId, String requestId) {
    List<ApprovalRequest> requests =
        jdbc.query(
            "SELECT * FROM ds_approval_request WHERE tenant_id = :tenantId AND id = :requestId AND deleted_at IS NULL",
            Map.of("tenantId", tenantId, "requestId", requestId),
            JdbcApprovalRepository::mapRequest);
    if (requests.isEmpty()) {
      return Optional.empty();
    }
    List<ApprovalItem> items =
        jdbc.query(
            "SELECT * FROM ds_approval_item WHERE tenant_id = :tenantId AND request_id = :requestId ORDER BY ordinal",
            Map.of("tenantId", tenantId, "requestId", requestId),
            JdbcApprovalRepository::mapItem);
    List<ApprovalEvent> events =
        jdbc.query(
            "SELECT * FROM ds_approval_event WHERE tenant_id = :tenantId AND request_id = :requestId ORDER BY created_at, id",
            Map.of("tenantId", tenantId, "requestId", requestId),
            JdbcApprovalRepository::mapEvent);
    return Optional.of(new ApprovalDetail(requests.get(0), items, events));
  }

  @Override
  public List<ApprovalRequest> findRequests(
      String tenantId,
      String visibleApplicantUserId,
      List<ApprovalStatus> statuses,
      String workOrderTypeKey,
      String applicant,
      String keyword,
      Instant createdFrom,
      Instant createdTo,
      int offset,
      int limit) {
    MapSqlParameterSource parameters =
        requestSearchParameters(
                tenantId,
                visibleApplicantUserId,
                statuses,
                workOrderTypeKey,
                applicant,
                keyword,
                createdFrom,
                createdTo)
            .addValue("offset", Math.max(0, offset))
            .addValue("limit", Math.max(1, Math.min(limit, 100)));
    return jdbc.query(
        """
        SELECT * FROM ds_approval_request
        WHERE tenant_id = :tenantId AND deleted_at IS NULL
          AND (:visibleApplicantUserId IS NULL OR applicant_user_id = :visibleApplicantUserId)
          AND (:statusesEmpty = 1 OR status IN (:statuses))
          AND (:workOrderTypeKey IS NULL OR work_order_type_key = :workOrderTypeKey)
          AND (:applicantContains IS NULL OR applicant_user_id LIKE :applicantContains
            OR applicant_display_name LIKE :applicantContains)
          AND (:keywordContains IS NULL OR request_no LIKE :keywordContains
            OR title LIKE :keywordContains OR summary LIKE :keywordContains
            OR connection_name LIKE :keywordContains
            OR applicant_user_id LIKE :keywordContains
            OR applicant_display_name LIKE :keywordContains)
          AND (:createdFrom IS NULL OR created_at >= :createdFrom)
          AND (:createdTo IS NULL OR created_at <= :createdTo)
        ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset
        """,
        parameters,
        JdbcApprovalRepository::mapRequest);
  }

  @Override
  public long countRequests(
      String tenantId,
      String visibleApplicantUserId,
      List<ApprovalStatus> statuses,
      String workOrderTypeKey,
      String applicant,
      String keyword,
      Instant createdFrom,
      Instant createdTo) {
    Long total =
        jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM ds_approval_request
            WHERE tenant_id = :tenantId AND deleted_at IS NULL
              AND (:visibleApplicantUserId IS NULL OR applicant_user_id = :visibleApplicantUserId)
              AND (:statusesEmpty = 1 OR status IN (:statuses))
              AND (:workOrderTypeKey IS NULL OR work_order_type_key = :workOrderTypeKey)
              AND (:applicantContains IS NULL OR applicant_user_id LIKE :applicantContains
                OR applicant_display_name LIKE :applicantContains)
              AND (:keywordContains IS NULL OR request_no LIKE :keywordContains
                OR title LIKE :keywordContains OR summary LIKE :keywordContains
                OR connection_name LIKE :keywordContains
                OR applicant_user_id LIKE :keywordContains
                OR applicant_display_name LIKE :keywordContains)
              AND (:createdFrom IS NULL OR created_at >= :createdFrom)
              AND (:createdTo IS NULL OR created_at <= :createdTo)
            """,
            requestSearchParameters(
                tenantId,
                visibleApplicantUserId,
                statuses,
                workOrderTypeKey,
                applicant,
                keyword,
                createdFrom,
                createdTo),
            Long.class);
    return total == null ? 0 : total;
  }

  private static MapSqlParameterSource requestSearchParameters(
      String tenantId,
      String visibleApplicantUserId,
      List<ApprovalStatus> statuses,
      String workOrderTypeKey,
      String applicant,
      String keyword,
      Instant createdFrom,
      Instant createdTo) {
    return new MapSqlParameterSource()
        .addValue("tenantId", tenantId)
        .addValue("visibleApplicantUserId", visibleApplicantUserId)
        .addValue("statusesEmpty", statuses == null || statuses.isEmpty() ? 1 : 0)
        .addValue(
            "statuses",
            statuses == null || statuses.isEmpty()
                ? List.of("__ALL__")
                : statuses.stream().map(Enum::name).toList())
        .addValue("workOrderTypeKey", blankToNull(workOrderTypeKey))
        .addValue("applicantContains", containsPattern(applicant))
        .addValue("keywordContains", containsPattern(keyword))
        .addValue("createdFrom", createdFrom == null ? null : timestamp(createdFrom))
        .addValue("createdTo", createdTo == null ? null : timestamp(createdTo));
  }

  private static String containsPattern(String value) {
    String normalized = blankToNull(value);
    return normalized == null ? null : "%" + normalized + "%";
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  @Transactional
  public boolean updateSqlPlan(
      ApprovalRequest request,
      long expectedRevision,
      ApprovalStatus expectedStatus,
      List<ApprovalItem> items,
      ApprovalEvent event) {
    Instant now = Instant.now();
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET content_json = :contentJson, content_version = content_version + 1,
              content_digest = :contentDigest, status = 'DRAFT', submitted_at = NULL,
              reviewer_user_id = NULL, reviewer_display_name = NULL, review_comment = NULL,
              revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :id AND revision = :expectedRevision
              AND status = :expectedStatus AND deleted_at IS NULL
            """,
            requestParameters(request)
                .addValue("expectedRevision", expectedRevision)
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("now", timestamp(now)));
    if (affected != 1) return false;
    for (ApprovalItem item : items) {
      jdbc.update(
          """
          UPDATE ds_approval_item
          SET sql_text = :sqlText, normalized_sql_digest = :normalizedSqlDigest
          WHERE tenant_id = :tenantId AND request_id = :requestId AND id = :id
          """,
          new MapSqlParameterSource()
              .addValue("sqlText", item.sqlText())
              .addValue("normalizedSqlDigest", item.normalizedSqlDigest())
              .addValue("tenantId", item.tenantId())
              .addValue("requestId", item.requestId())
              .addValue("id", item.id()));
    }
    releaseResourceClaims(request.tenantId(), request.id(), now);
    insertEvent(event);
    return true;
  }

  @Override
  @Transactional
  public boolean transition(
      String tenantId,
      String requestId,
      long expectedRevision,
      ApprovalStatus expectedStatus,
      ApprovalStatus targetStatus,
      String reviewerUserId,
      String reviewerDisplayName,
      String reviewComment,
      ApprovalEvent event) {
    Instant now = Instant.now();
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("requestId", requestId)
            .addValue("expectedRevision", expectedRevision)
            .addValue("expectedStatus", expectedStatus.name())
            .addValue("targetStatus", targetStatus.name())
            .addValue("reviewerUserId", reviewerUserId)
            .addValue("reviewerDisplayName", reviewerDisplayName)
            .addValue("reviewComment", reviewComment)
            .addValue("now", Timestamp.from(now));
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET status = :targetStatus, reviewer_user_id = :reviewerUserId,
              reviewer_display_name = :reviewerDisplayName, review_comment = :reviewComment,
              submitted_at = CASE WHEN :targetStatus = 'SUBMITTED' THEN :now ELSE submitted_at END,
              approved_at = CASE WHEN :targetStatus = 'APPROVED' THEN :now ELSE approved_at END,
              rejected_at = CASE WHEN :targetStatus = 'REJECTED' THEN :now ELSE rejected_at END,
              finished_at = CASE WHEN :targetStatus IN ('SUCCEEDED','FAILED','CANCELLED') THEN :now ELSE finished_at END,
              revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :requestId
              AND revision = :expectedRevision AND status = :expectedStatus AND deleted_at IS NULL
            """,
            parameters);
    if (affected == 1) {
      if (targetStatus == ApprovalStatus.REJECTED || targetStatus == ApprovalStatus.CANCELLED) {
        releaseResourceClaims(tenantId, requestId, now);
      }
      insertEvent(event);
      return true;
    }
    return false;
  }

  @Override
  @Transactional
  public boolean submitWithResourceClaims(
      String tenantId,
      String requestId,
      long expectedRevision,
      List<String> resourceKeys,
      String actorUserId,
      ApprovalEvent event) {
    Instant now = Instant.now();
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET status = 'SUBMITTED', submitted_at = :now, revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :requestId AND revision = :expectedRevision
              AND status = 'DRAFT' AND deleted_at IS NULL
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("requestId", requestId)
                .addValue("expectedRevision", expectedRevision)
                .addValue("now", timestamp(now)));
    if (affected != 1) {
      return false;
    }
    for (String resourceKey : resourceKeys.stream().distinct().sorted().toList()) {
      jdbc.update(
          """
          INSERT INTO ds_approval_resource_claim
            (id, tenant_id, request_id, resource_key, claim_kind, active_key, created_at)
          VALUES (:id, :tenantId, :requestId, :resourceKey, 'DDL_TARGET', 'ACTIVE', :createdAt)
          """,
          new MapSqlParameterSource()
              .addValue("id", Ulid.next())
              .addValue("tenantId", tenantId)
              .addValue("requestId", requestId)
              .addValue("resourceKey", resourceKey)
              .addValue("createdAt", timestamp(now)));
    }
    insertEvent(event);
    return true;
  }

  @Override
  @Transactional
  public int beginExecution(
      String tenantId,
      String requestId,
      long expectedRevision,
      String actorUserId,
      ApprovalEvent event) {
    Instant now = Instant.now();
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET status = 'RUNNING', execution_attempt = execution_attempt + 1,
              latest_execution_status = 'RUNNING', execution_owner = :actorUserId,
              revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :requestId AND revision = :expectedRevision
              AND status = 'APPROVED' AND execution_mode = 'MANUAL_TRIGGER' AND deleted_at IS NULL
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("requestId", requestId)
                .addValue("expectedRevision", expectedRevision)
                .addValue("actorUserId", actorUserId)
                .addValue("now", timestamp(now)));
    if (affected != 1) {
      return -1;
    }
    insertEvent(event);
    return jdbc.queryForObject(
        "SELECT execution_attempt FROM ds_approval_request WHERE tenant_id = :tenantId AND id = :requestId",
        Map.of("tenantId", tenantId, "requestId", requestId),
        Integer.class);
  }

  @Override
  public int retryExecution(
      String tenantId,
      String requestId,
      long expectedRevision,
      String actorUserId,
      ApprovalEvent event) {
    Instant now = Instant.now();
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET status = 'RUNNING', execution_attempt = execution_attempt + 1,
              latest_execution_status = 'RUNNING', execution_owner = :actorUserId,
              revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :requestId AND revision = :expectedRevision
              AND status IN ('FAILED', 'RECONCILING') AND deleted_at IS NULL
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("requestId", requestId)
                .addValue("expectedRevision", expectedRevision)
                .addValue("actorUserId", actorUserId)
                .addValue("now", timestamp(now)));
    if (affected != 1) {
      return -1;
    }
    insertEvent(event);
    return jdbc.queryForObject(
        "SELECT execution_attempt FROM ds_approval_request WHERE tenant_id = :tenantId AND id = :requestId",
        Map.of("tenantId", tenantId, "requestId", requestId),
        Integer.class);
  }

  @Override
  public java.util.Set<String> findSucceededItemIds(String tenantId, String requestId) {
    return new java.util.TreeSet<>(
        jdbc.queryForList(
            """
            SELECT DISTINCT item_id FROM ds_approval_execution
            WHERE tenant_id = :tenantId AND request_id = :requestId AND status = 'SUCCEEDED'
            """,
            Map.of("tenantId", tenantId, "requestId", requestId),
            String.class));
  }

  @Override
  public void createSkippedExecution(
      String tenantId, String requestId, String itemId, int attemptNo, int ordinal) {
    Instant now = Instant.now();
    jdbc.update(
        """
        INSERT INTO ds_approval_execution
          (id, tenant_id, request_id, item_id, attempt_no, ordinal, status, query_id,
           started_at, finished_at, duration_ms, created_at, updated_at)
        VALUES (:id, :tenantId, :requestId, :itemId, :attemptNo, :ordinal, 'SKIPPED',
          :queryId, :now, :now, 0, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("id", Ulid.next())
            .addValue("tenantId", tenantId)
            .addValue("requestId", requestId)
            .addValue("itemId", itemId)
            .addValue("attemptNo", attemptNo)
            .addValue("ordinal", ordinal)
            .addValue("queryId", "skip:" + requestId + ":" + attemptNo + ":" + itemId)
            .addValue("now", timestamp(now)));
  }

  @Override
  public void renewExecutionLease(String tenantId, String requestId, Instant leaseUntil) {
    jdbc.update(
        """
        UPDATE ds_approval_request
        SET execution_lease_until = :leaseUntil, updated_at = :now
        WHERE tenant_id = :tenantId AND id = :requestId
          AND status = 'RUNNING' AND deleted_at IS NULL
        """,
        new MapSqlParameterSource()
            .addValue("leaseUntil", timestamp(leaseUntil))
            .addValue("now", timestamp(Instant.now()))
            .addValue("tenantId", tenantId)
            .addValue("requestId", requestId));
  }

  @Override
  public List<ApprovalRequest> findStuckRunningRequests() {
    return jdbc.query(
        """
        SELECT * FROM ds_approval_request
        WHERE status = 'RUNNING' AND deleted_at IS NULL
          AND execution_lease_until IS NOT NULL AND execution_lease_until < :now
        ORDER BY updated_at
        LIMIT 10
        """,
        new MapSqlParameterSource().addValue("now", timestamp(Instant.now())),
        JdbcApprovalRepository::mapRequest);
  }

  @Override
  public List<ApprovalRequest> findClaimableQueuedRequests(int limit) {
    return jdbc.query(
        """
        SELECT * FROM ds_approval_request
        WHERE status = 'QUEUED' AND deleted_at IS NULL
          AND (execution_lease_until IS NULL OR execution_lease_until < :now)
        ORDER BY updated_at
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("now", timestamp(Instant.now()))
            .addValue("limit", limit),
        JdbcApprovalRepository::mapRequest);
  }

  @Override
  public int claimQueued(
      String tenantId,
      String requestId,
      long expectedRevision,
      Instant leaseUntil,
      String actorUserId,
      ApprovalEvent event) {
    Instant now = Instant.now();
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET status = 'RUNNING', execution_attempt = execution_attempt + 1,
              latest_execution_status = 'RUNNING', execution_owner = :actorUserId,
              execution_lease_until = :leaseUntil, revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :requestId AND revision = :expectedRevision
              AND status = 'QUEUED' AND deleted_at IS NULL
              AND (execution_lease_until IS NULL OR execution_lease_until < :now)
            """,
            new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("requestId", requestId)
                .addValue("expectedRevision", expectedRevision)
                .addValue("leaseUntil", timestamp(leaseUntil))
                .addValue("actorUserId", actorUserId)
                .addValue("now", timestamp(now)));
    if (affected != 1) {
      return -1;
    }
    insertEvent(event);
    return jdbc.queryForObject(
        "SELECT execution_attempt FROM ds_approval_request WHERE tenant_id = :tenantId AND id = :requestId",
        Map.of("tenantId", tenantId, "requestId", requestId),
        Integer.class);
  }

  @Override
  public String createExecution(
      String tenantId,
      String requestId,
      String itemId,
      int attemptNo,
      int ordinal,
      String queryId) {
    Instant now = Instant.now();
    String id = Ulid.next();
    jdbc.update(
        """
        INSERT INTO ds_approval_execution
          (id, tenant_id, request_id, item_id, attempt_no, ordinal, status, query_id,
           started_at, created_at, updated_at)
        VALUES (:id, :tenantId, :requestId, :itemId, :attemptNo, :ordinal, 'RUNNING',
          :queryId, :now, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", tenantId)
            .addValue("requestId", requestId)
            .addValue("itemId", itemId)
            .addValue("attemptNo", attemptNo)
            .addValue("ordinal", ordinal)
            .addValue("queryId", queryId)
            .addValue("now", timestamp(now)));
    return id;
  }

  @Override
  public String createNodeExecution(
      String tenantId, String executionId, String nodeKey, String host, Integer port) {
    Instant now = Instant.now();
    String id = Ulid.next();
    jdbc.update(
        """
        INSERT INTO ds_approval_node_execution
          (id, tenant_id, execution_id, node_key, host, port, status,
           started_at, created_at, updated_at)
        VALUES (:id, :tenantId, :executionId, :nodeKey, :host, :port, 'RUNNING',
          :now, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("tenantId", tenantId)
            .addValue("executionId", executionId)
            .addValue("nodeKey", nodeKey)
            .addValue("host", host)
            .addValue("port", port)
            .addValue("now", timestamp(now)));
    return id;
  }

  @Override
  public void finishExecution(
      String tenantId,
      String executionId,
      boolean succeeded,
      long durationMs,
      String errorCode,
      String safeMessage) {
    Instant now = Instant.now();
    jdbc.update(
        """
        UPDATE ds_approval_execution
        SET status = :status, finished_at = :now, duration_ms = :durationMs,
          error_code = :errorCode, safe_message = :safeMessage, updated_at = :now
        WHERE tenant_id = :tenantId AND id = :executionId AND status = 'RUNNING'
        """,
        new MapSqlParameterSource()
            .addValue("status", succeeded ? "SUCCEEDED" : "FAILED")
            .addValue("now", timestamp(now))
            .addValue("durationMs", durationMs)
            .addValue("errorCode", errorCode)
            .addValue("safeMessage", safeMessage)
            .addValue("tenantId", tenantId)
            .addValue("executionId", executionId));
  }

  @Override
  public void finishNodeExecution(
      String tenantId,
      String nodeExecutionId,
      boolean succeeded,
      long durationMs,
      String errorCode,
      String safeMessage) {
    Instant now = Instant.now();
    jdbc.update(
        """
        UPDATE ds_approval_node_execution
        SET status = :status, finished_at = :now, duration_ms = :durationMs,
          error_code = :errorCode, safe_message = :safeMessage, updated_at = :now
        WHERE tenant_id = :tenantId AND id = :id AND status = 'RUNNING'
        """,
        new MapSqlParameterSource()
            .addValue("status", succeeded ? "SUCCEEDED" : "FAILED")
            .addValue("now", timestamp(now))
            .addValue("durationMs", durationMs)
            .addValue("errorCode", errorCode)
            .addValue("safeMessage", safeMessage)
            .addValue("tenantId", tenantId)
            .addValue("id", nodeExecutionId));
  }

  @Override
  public List<ApprovalExecution> findExecutions(String tenantId, String requestId) {
    return jdbc.query(
        """
        SELECT * FROM ds_approval_execution
        WHERE tenant_id = :tenantId AND request_id = :requestId
        ORDER BY attempt_no DESC, ordinal
        """,
        Map.of("tenantId", tenantId, "requestId", requestId),
        JdbcApprovalRepository::mapExecution);
  }

  @Override
  public List<ApprovalNodeExecution> findNodeExecutions(
      String tenantId, String executionId, String status, int offset, int limit) {
    return jdbc.query(
        """
        SELECT * FROM ds_approval_node_execution
        WHERE tenant_id = :tenantId AND execution_id = :executionId
          AND (:status IS NULL OR status = :status)
        ORDER BY CASE WHEN status = 'FAILED' THEN 0 ELSE 1 END, host, port
        LIMIT :limit OFFSET :offset
        """,
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("executionId", executionId)
            .addValue("status", status)
            .addValue("limit", Math.max(1, Math.min(limit, 200)))
            .addValue("offset", Math.max(0, offset)),
        JdbcApprovalRepository::mapNodeExecution);
  }

  @Override
  @Transactional
  public void finishRequestExecution(
      String tenantId,
      String requestId,
      long expectedRevision,
      ApprovalStatus expectedStatus,
      ApprovalStatus targetStatus,
      String actorUserId,
      ApprovalEvent event) {
    Instant now = Instant.now();
    int affected =
        jdbc.update(
            """
            UPDATE ds_approval_request
            SET status = :targetStatus, latest_execution_status = :targetStatus,
              execution_owner = NULL, execution_lease_until = NULL, finished_at = :now,
              revision = revision + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :requestId AND status = :expectedStatus
              AND revision = :expectedRevision
            """,
            new MapSqlParameterSource()
                .addValue("targetStatus", targetStatus.name())
                .addValue("expectedStatus", expectedStatus.name())
                .addValue("expectedRevision", expectedRevision)
                .addValue("now", timestamp(now))
                .addValue("tenantId", tenantId)
                .addValue("requestId", requestId));
    if (affected != 1) {
      throw new IllegalStateException("Approval execution state changed concurrently");
    }
    if (targetStatus == ApprovalStatus.SUCCEEDED || targetStatus == ApprovalStatus.CANCELLED) {
      releaseResourceClaims(tenantId, requestId, now);
    }
    insertEvent(event);
  }

  private void releaseResourceClaims(String tenantId, String requestId, Instant now) {
    jdbc.update(
        """
        UPDATE ds_approval_resource_claim
        SET active_key = NULL, released_at = :now
        WHERE tenant_id = :tenantId AND request_id = :requestId AND active_key = 'ACTIVE'
        """,
        Map.of("now", timestamp(now), "tenantId", tenantId, "requestId", requestId));
  }

  private void insertItem(ApprovalItem item) {
    jdbc.update(
        """
        INSERT INTO ds_approval_item (
          id, tenant_id, request_id, ordinal, operation_kind, sql_text,
          normalized_sql_digest, object_refs_json, risk_level, warnings_json,
          idempotency_strategy, precondition_json, created_at)
        VALUES (:id, :tenantId, :requestId, :ordinal, :operationKind, :sqlText,
          :normalizedSqlDigest, :objectRefsJson, :riskLevel, :warningsJson,
          :idempotencyStrategy, :preconditionJson, :createdAt)
        """,
        new MapSqlParameterSource()
            .addValue("id", item.id())
            .addValue("tenantId", item.tenantId())
            .addValue("requestId", item.requestId())
            .addValue("ordinal", item.ordinal())
            .addValue("operationKind", item.operationKind().name())
            .addValue("sqlText", item.sqlText())
            .addValue("normalizedSqlDigest", item.normalizedSqlDigest())
            .addValue("objectRefsJson", item.objectRefsJson())
            .addValue("riskLevel", item.riskLevel())
            .addValue("warningsJson", item.warningsJson())
            .addValue("idempotencyStrategy", item.idempotencyStrategy())
            .addValue("preconditionJson", item.preconditionJson())
            .addValue("createdAt", timestamp(item.createdAt())));
  }

  private void insertEvent(ApprovalEvent event) {
    jdbc.update(
        """
        INSERT INTO ds_approval_event (
          id, tenant_id, request_id, event_type, actor_user_id, actor_display_name,
          safe_message, details_json, created_at)
        VALUES (:id, :tenantId, :requestId, :eventType, :actorUserId,
          :actorDisplayName, :safeMessage, :detailsJson, :createdAt)
        """,
        new MapSqlParameterSource()
            .addValue("id", event.id())
            .addValue("tenantId", event.tenantId())
            .addValue("requestId", event.requestId())
            .addValue("eventType", event.eventType())
            .addValue("actorUserId", event.actorUserId())
            .addValue("actorDisplayName", event.actorDisplayName())
            .addValue("safeMessage", event.safeMessage())
            .addValue("detailsJson", event.detailsJson())
            .addValue("createdAt", timestamp(event.createdAt())));
  }

  private static MapSqlParameterSource typeParameters(ApprovalTypeDefinition d) {
    return new MapSqlParameterSource()
        .addValue("id", d.id())
        .addValue("tenantId", d.tenantId())
        .addValue("typeKey", d.typeKey())
        .addValue("handlerKey", d.handlerKey())
        .addValue("nameI18nJson", d.nameI18nJson())
        .addValue("descriptionI18nJson", d.descriptionI18nJson())
        .addValue("generatorKey", d.generatorKey())
        .addValue("allowedOperationKindsJson", d.allowedOperationKindsJson())
        .addValue("generationRuleJson", d.generationRuleJson())
        .addValue("applicableConnectionsJson", d.applicableConnectionsJson())
        .addValue("riskPolicyJson", d.riskPolicyJson())
        .addValue("status", d.status())
        .addValue("definitionRevision", d.definitionRevision())
        .addValue("checksum", d.checksum())
        .addValue("createdBy", d.createdBy())
        .addValue("updatedBy", d.updatedBy())
        .addValue("enabledBy", d.enabledBy())
        .addValue("createdAt", timestamp(d.createdAt()))
        .addValue("updatedAt", timestamp(d.updatedAt()))
        .addValue("enabledAt", timestamp(d.enabledAt()));
  }

  private static MapSqlParameterSource requestParameters(ApprovalRequest r) {
    return new MapSqlParameterSource()
        .addValue("id", r.id())
        .addValue("tenantId", r.tenantId())
        .addValue("requestNo", r.requestNo())
        .addValue("workOrderTypeKey", r.workOrderTypeKey())
        .addValue("workOrderTypeRevision", r.workOrderTypeRevision())
        .addValue("typeDefinitionChecksum", r.typeDefinitionChecksum())
        .addValue("title", r.title())
        .addValue("summary", r.summary())
        .addValue("applicantUserId", r.applicantUserId())
        .addValue("applicantDisplayName", r.applicantDisplayName())
        .addValue("sourceSessionId", r.sourceSessionId())
        .addValue("sourceRunId", r.sourceRunId())
        .addValue("connectionId", r.connectionId())
        .addValue("connectionName", r.connectionName())
        .addValue("status", r.status().name())
        .addValue("contentJson", r.contentJson())
        .addValue("contentVersion", r.contentVersion())
        .addValue("contentDigest", r.contentDigest())
        .addValue("executionMode", r.executionMode())
        .addValue("executionAttempt", r.executionAttempt())
        .addValue("revision", r.revision())
        .addValue("createdAt", timestamp(r.createdAt()))
        .addValue("updatedAt", timestamp(r.updatedAt()))
        .addValue("planVersion", r.planVersion())
        .addValue("planHash", r.planHash())
        .addValue("envSnapshotJson", r.envSnapshotJson())
        .addValue("policyVersionRef", r.policyVersionRef());
  }

  private static ApprovalTypeDefinition mapType(ResultSet rs, int row) throws SQLException {
    return new ApprovalTypeDefinition(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("type_key"),
        rs.getString("handler_key"),
        rs.getString("name_i18n_json"),
        rs.getString("description_i18n_json"),
        rs.getString("generator_key"),
        rs.getString("allowed_operation_kinds_json"),
        rs.getString("generation_rule_json"),
        rs.getString("applicable_connections_json"),
        rs.getString("risk_policy_json"),
        rs.getString("status"),
        rs.getLong("definition_revision"),
        rs.getString("checksum"),
        rs.getString("created_by"),
        rs.getString("updated_by"),
        rs.getString("enabled_by"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"),
        instant(rs, "enabled_at"));
  }

  private static ApprovalRequest mapRequest(ResultSet rs, int row) throws SQLException {
    return new ApprovalRequest(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("request_no"),
        rs.getString("work_order_type_key"),
        rs.getLong("work_order_type_revision"),
        rs.getString("type_definition_checksum"),
        rs.getString("title"),
        rs.getString("summary"),
        rs.getString("applicant_user_id"),
        rs.getString("applicant_display_name"),
        rs.getString("source_session_id"),
        rs.getString("source_run_id"),
        rs.getString("connection_id"),
        rs.getString("connection_name"),
        ApprovalStatus.valueOf(rs.getString("status")),
        rs.getString("content_json"),
        rs.getLong("content_version"),
        rs.getString("content_digest"),
        rs.getString("execution_mode"),
        rs.getInt("execution_attempt"),
        rs.getString("reviewer_user_id"),
        rs.getString("reviewer_display_name"),
        rs.getString("review_comment"),
        rs.getLong("revision"),
        instant(rs, "created_at"),
        instant(rs, "submitted_at"),
        instant(rs, "approved_at"),
        instant(rs, "rejected_at"),
        instant(rs, "finished_at"),
        instant(rs, "updated_at"),
        rs.getInt("plan_version"),
        rs.getString("plan_hash"),
        rs.getString("env_snapshot_json"),
        rs.getString("policy_version_ref"));
  }

  private static ApprovalItem mapItem(ResultSet rs, int row) throws SQLException {
    return new ApprovalItem(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("request_id"),
        rs.getInt("ordinal"),
        DdlOperationKind.valueOf(rs.getString("operation_kind")),
        rs.getString("sql_text"),
        rs.getString("normalized_sql_digest"),
        rs.getString("object_refs_json"),
        rs.getString("risk_level"),
        rs.getString("warnings_json"),
        rs.getString("idempotency_strategy"),
        rs.getString("precondition_json"),
        instant(rs, "created_at"));
  }

  private static ApprovalEvent mapEvent(ResultSet rs, int row) throws SQLException {
    return new ApprovalEvent(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("request_id"),
        rs.getString("event_type"),
        rs.getString("actor_user_id"),
        rs.getString("actor_display_name"),
        rs.getString("safe_message"),
        rs.getString("details_json"),
        instant(rs, "created_at"));
  }

  private static ApprovalExecution mapExecution(ResultSet rs, int row) throws SQLException {
    return new ApprovalExecution(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("request_id"),
        rs.getString("item_id"),
        rs.getInt("attempt_no"),
        rs.getInt("ordinal"),
        rs.getString("status"),
        rs.getString("query_id"),
        instant(rs, "started_at"),
        instant(rs, "finished_at"),
        nullableLong(rs, "duration_ms"),
        rs.getString("error_code"),
        rs.getString("safe_message"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private static ApprovalNodeExecution mapNodeExecution(ResultSet rs, int row) throws SQLException {
    return new ApprovalNodeExecution(
        rs.getString("id"),
        rs.getString("tenant_id"),
        rs.getString("execution_id"),
        rs.getString("node_key"),
        rs.getString("host"),
        nullableInteger(rs, "port"),
        rs.getString("status"),
        nullableLong(rs, "duration_ms"),
        rs.getString("error_code"),
        rs.getString("safe_message"),
        instant(rs, "started_at"),
        instant(rs, "finished_at"),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));
  }

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private static Long nullableLong(ResultSet rs, String column) throws SQLException {
    Number value = (Number) rs.getObject(column);
    return value == null ? null : value.longValue();
  }

  private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
    Number value = (Number) rs.getObject(column);
    return value == null ? null : value.intValue();
  }
}
