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

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalEvent;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
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
  @Transactional
  public void createDraft(
      ApprovalRequest request, List<ApprovalItem> items, ApprovalEvent createdEvent) {
    jdbc.update(
        """
        INSERT INTO ds_approval_request (
          id, tenant_id, request_no, type, work_order_type_key, work_order_type_revision,
          type_definition_checksum, title, summary, applicant_user_id, applicant_display_name,
          source_session_id, source_run_id, connection_id, connection_name, status,
          content_json, content_version, content_digest, execution_mode, execution_attempt,
          revision, created_at, updated_at)
        VALUES (
          :id, :tenantId, :requestNo, 'CLICKHOUSE_DDL', :workOrderTypeKey,
          :workOrderTypeRevision, :typeDefinitionChecksum, :title, :summary,
          :applicantUserId, :applicantDisplayName, :sourceSessionId, :sourceRunId,
          :connectionId, :connectionName, :status, :contentJson, :contentVersion,
          :contentDigest, :executionMode, :executionAttempt, :revision, :createdAt, :updatedAt)
        """,
        requestParameters(request));
    for (ApprovalItem item : items) {
      insertItem(item);
    }
    insertEvent(createdEvent);
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
      String tenantId, String applicantUserId, ApprovalStatus status, int limit) {
    MapSqlParameterSource parameters =
        new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("applicantUserId", applicantUserId)
            .addValue("status", status == null ? null : status.name())
            .addValue("limit", Math.max(1, Math.min(limit, 200)));
    return jdbc.query(
        """
        SELECT * FROM ds_approval_request
        WHERE tenant_id = :tenantId AND deleted_at IS NULL
          AND (:applicantUserId IS NULL OR applicant_user_id = :applicantUserId)
          AND (:status IS NULL OR status = :status)
        ORDER BY updated_at DESC, id DESC LIMIT :limit
        """,
        parameters,
        JdbcApprovalRepository::mapRequest);
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
      insertEvent(event);
      return true;
    }
    return false;
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
        .addValue("updatedAt", timestamp(r.updatedAt()));
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
        instant(rs, "updated_at"));
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

  private static Timestamp timestamp(Instant value) {
    return value == null ? null : Timestamp.from(value);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }
}
