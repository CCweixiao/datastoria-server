package io.github.ccweixiao.datastoria.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTransitionRequest;
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
  public Mono<ResponseEntity<List<ApprovalRequest>>> list(
      @RequestParam(required = false) ApprovalStatus status,
      @RequestParam(defaultValue = "50") int limit) {
    return IdentityContext.current()
        .flatMap(identity -> service.list(status, limit, identity))
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

  private ApprovalWorkOrderTypeResponse summary(ApprovalTypeDefinition type) {
    return new ApprovalWorkOrderTypeResponse(
        type.typeKey(),
        type.nameI18nJson(),
        type.descriptionI18nJson(),
        requiredFields(type.generatorKey()),
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
