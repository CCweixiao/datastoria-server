package io.github.ccweixiao.datastoria.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalNodeExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalPageResponse;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalSqlPlanUpdateRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTransitionRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTypeUpdateRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalWorkOrderTypeResponse;
import io.github.ccweixiao.datastoria.common.dto.approval.DdlApprovalPrepareRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.DdlApprovalPrepareResponse;
import io.github.ccweixiao.datastoria.common.identity.AdminAccess;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.approval.ApprovalCommandService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
public class ApprovalController {

  private final ApprovalCommandService service;

  public ApprovalController(ApprovalCommandService service) {
    this.service = service;
  }

  @GetMapping("/approval-types/clickhouse-ddl/capabilities")
  public Mono<ResponseEntity<List<ApprovalWorkOrderTypeResponse>>> capabilities(
      @RequestParam String connectionId) {
    return IdentityContext.current()
        .flatMap(identity -> service.listTypes(connectionId, identity))
        .map(types -> types.stream().map(this::summary).toList())
        .map(ResponseEntity::ok);
  }

  @PostMapping("/approval-types/clickhouse-ddl/prepare")
  public Mono<ResponseEntity<DdlApprovalPrepareResponse>> prepare(
      @RequestBody DdlApprovalPrepareRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.prepare(request, identity))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/approvals")
  public Mono<ResponseEntity<ApprovalPageResponse>> list(
      @RequestParam(required = false, name = "status") List<ApprovalStatus> statuses,
      @RequestParam(required = false) String workOrderTypeKey,
      @RequestParam(required = false) String applicant,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Instant createdFrom,
      @RequestParam(required = false) Instant createdTo,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    return IdentityContext.current()
        .flatMap(
            identity ->
                service.list(
                    statuses,
                    workOrderTypeKey,
                    applicant,
                    keyword,
                    createdFrom,
                    createdTo,
                    page,
                    pageSize,
                    identity))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/approvals/{id}")
  public Mono<ResponseEntity<ApprovalDetail>> detail(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> service.detail(id, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/approvals/{id}/submit")
  public Mono<ResponseEntity<ApprovalDetail>> submit(
      @PathVariable String id, @RequestBody ApprovalTransitionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.submit(id, request, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/approvals/{id}/interrupt")
  public Mono<ResponseEntity<ApprovalDetail>> interrupt(
      @PathVariable String id, @RequestBody ApprovalTransitionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.interrupt(id, request, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/admin/approvals/{id}/approve")
  @AdminAccess
  public Mono<ResponseEntity<ApprovalDetail>> approve(
      @PathVariable String id, @RequestBody ApprovalTransitionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.review(id, request, true, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/admin/approvals/{id}/reject")
  @AdminAccess
  public Mono<ResponseEntity<ApprovalDetail>> reject(
      @PathVariable String id, @RequestBody ApprovalTransitionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.review(id, request, false, identity))
        .map(ResponseEntity::ok);
  }

  @PutMapping("/admin/approvals/{id}/sql-plan")
  @AdminAccess
  public Mono<ResponseEntity<ApprovalDetail>> updateSqlPlan(
      @PathVariable String id, @RequestBody ApprovalSqlPlanUpdateRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.updateSqlPlan(id, request, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/admin/approvals/{id}/execute")
  @AdminAccess
  public Mono<ResponseEntity<ApprovalDetail>> execute(
      @PathVariable String id, @RequestBody ApprovalTransitionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.execute(id, request, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/admin/approvals/{id}/retry")
  @AdminAccess
  public Mono<ResponseEntity<ApprovalDetail>> retry(
      @PathVariable String id, @RequestBody ApprovalTransitionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.retry(id, request, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/admin/approvals/{id}/close")
  @AdminAccess
  public Mono<ResponseEntity<ApprovalDetail>> closeFailed(
      @PathVariable String id, @RequestBody ApprovalTransitionRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.closeFailed(id, request, identity))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/admin/approvals/{id}/executions")
  @AdminAccess
  public Mono<ResponseEntity<List<ApprovalExecution>>> executions(@PathVariable String id) {
    return IdentityContext.current()
        .flatMap(identity -> service.executions(id, identity))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/admin/approvals/{id}/executions/{executionId}/nodes")
  @AdminAccess
  public Mono<ResponseEntity<List<ApprovalNodeExecution>>> nodeExecutions(
      @PathVariable String id,
      @PathVariable String executionId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "50") int limit) {
    return IdentityContext.current()
        .flatMap(
            identity -> service.nodeExecutions(id, executionId, status, offset, limit, identity))
        .map(ResponseEntity::ok);
  }

  @GetMapping("/admin/approval-types/clickhouse-ddl")
  @AdminAccess
  public Mono<ResponseEntity<List<ApprovalTypeDefinition>>> typeDefinitions() {
    return IdentityContext.current().flatMap(service::listTypeDefinitions).map(ResponseEntity::ok);
  }

  @PutMapping("/admin/approval-types/clickhouse-ddl/{typeKey}")
  @AdminAccess
  public Mono<ResponseEntity<ApprovalTypeDefinition>> updateTypeDefinition(
      @PathVariable String typeKey, @RequestBody ApprovalTypeUpdateRequest request) {
    return IdentityContext.current()
        .flatMap(identity -> service.updateTypeDefinition(typeKey, request, identity))
        .map(ResponseEntity::ok);
  }

  private ApprovalWorkOrderTypeResponse summary(ApprovalTypeDefinition type) {
    return new ApprovalWorkOrderTypeResponse(
        type.typeKey(),
        type.nameI18nJson(),
        type.descriptionI18nJson(),
        requiredFields(type.generatorKey()),
        intentSchema(type.generatorKey()),
        safeRuleSummary(type.generatorKey()),
        type.definitionRevision());
  }

  private static List<String> requiredFields(String generatorKey) {
    return switch (generatorKey) {
      case "create_local_distributed_table" -> List.of(
          "database", "table", "cluster", "columns", "orderBy", "shardingKey");
      case "add_column", "modify_column" -> List.of("database", "table", "column", "type");
      case "drop_column" -> List.of("database", "table", "column");
      case "add_index" -> List.of(
          "database", "table", "index", "column", "indexType", "granularity");
      default -> List.of();
    };
  }

  private static List<ApprovalWorkOrderTypeResponse.IntentField> intentSchema(String generatorKey) {
    return switch (generatorKey) {
      case "create_local_distributed_table" -> List.of(
          field("database", "identifier", "user-provided"),
          field("table", "identifier", "user-provided"),
          field("cluster", "identifier", "user-provided"),
          field("columns", "array", "mixed"),
          field("orderBy", "array", "agent-derived"),
          field("shardingKey", "identifier", "agent-derived"));
      case "add_column", "modify_column" -> List.of(
          field("database", "identifier", "user-provided"),
          field("table", "identifier", "user-provided"),
          field("column", "identifier", "user-provided"),
          field("type", "columnType", "user-provided"));
      case "drop_column" -> List.of(
          field("database", "identifier", "user-provided"),
          field("table", "identifier", "user-provided"),
          field("column", "identifier", "user-provided"));
      case "add_index" -> List.of(
          field("database", "identifier", "user-provided"),
          field("table", "identifier", "user-provided"),
          field("index", "identifier", "user-provided"),
          field("column", "identifier", "user-provided"),
          field("indexType", "identifier", "user-provided"),
          field("granularity", "number", "user-provided"));
      default -> List.of();
    };
  }

  private static ApprovalWorkOrderTypeResponse.IntentField field(
      String name, String type, String source) {
    return new ApprovalWorkOrderTypeResponse.IntentField(name, type, true, source);
  }

  private static String safeRuleSummary(String generatorKey) {
    return switch (generatorKey) {
      case "create_local_distributed_table" -> "CREATE_LOCAL_AND_DISTRIBUTED_PAIR";
      case "modify_column", "drop_column" -> "PROTECT_KEY_COLUMNS";
      case "add_column" -> "REQUIRE_MISSING_COLUMN";
      case "add_index" -> "VALIDATE_SKIPPING_INDEX";
      default -> "SERVER_VALIDATED";
    };
  }
}
