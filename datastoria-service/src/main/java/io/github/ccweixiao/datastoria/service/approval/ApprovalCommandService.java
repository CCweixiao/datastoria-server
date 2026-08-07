package io.github.ccweixiao.datastoria.service.approval;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalEvent;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalNodeExecution;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionResponse;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalPageResponse;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalSqlPlanUpdateRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTransitionRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTypeUpdateRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.DdlApprovalPrepareRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.DdlApprovalPrepareResponse;
import io.github.ccweixiao.datastoria.common.error.AdminAccessRequiredException;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.ConflictException;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.PlainTextException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ApprovalRepository;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class ApprovalCommandService {

  private static final DateTimeFormatter REQUEST_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private static final int DRAIN_BATCH = 10;
  private static final java.time.Duration EXECUTION_LEASE_DURATION =
      java.time.Duration.ofMinutes(10);
  private static final java.time.Duration APPROVAL_EXPIRY = java.time.Duration.ofDays(7);

  private final ApprovalRepository repository;
  private final UserAccountRepository users;
  private final DdlWorkOrderTypeCatalog catalog;
  private final DdlPlanCompiler compiler;
  private final DdlSchemaInspector schemaInspector;
  private final ClickHouseConnectionService connections;
  private final ObjectMapper mapper;
  private final Scheduler jdbcScheduler;

  public ApprovalCommandService(
      ApprovalRepository repository,
      UserAccountRepository users,
      DdlWorkOrderTypeCatalog catalog,
      DdlPlanCompiler compiler,
      DdlSchemaInspector schemaInspector,
      ClickHouseConnectionService connections,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.repository = repository;
    this.users = users;
    this.catalog = catalog;
    this.compiler = compiler;
    this.schemaInspector = schemaInspector;
    this.connections = connections;
    this.mapper = mapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<DdlApprovalPrepareResponse> prepare(
      DdlApprovalPrepareRequest command, Identity identity) {
    validatePrepare(command);
    ApprovalTypeDefinition definition =
        catalog.requireEnabled(identity.tenantId(), command.workOrderTypeKey());
    return connections
        .findById(command.connectionId(), identity)
        .flatMap(
            connection ->
                schema(command, identity)
                    .map(
                        schema ->
                            createDraft(
                                command,
                                identity,
                                connection,
                                definition,
                                schema,
                                compiler.compile(command.intent(), definition, schema))))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalDetail> submit(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    validateTransition(command);
    return Mono.fromCallable(() -> requireVisible(requestId, identity, false))
        .subscribeOn(jdbcScheduler)
        .flatMap(
            detail -> {
              ApprovalRequest request = detail.request();
              if (!request.contentDigest().equals(command.contentDigest())) {
                return Mono.error(new ConflictException(ApiErrorCode.APPROVAL_CONTENT_CHANGED));
              }
              ApprovalTypeDefinition current =
                  catalog.requireEnabled(request.tenantId(), request.workOrderTypeKey());
              if (current.definitionRevision() != request.workOrderTypeRevision()
                  || !current.checksum().equals(request.typeDefinitionChecksum())) {
                return Mono.error(new ConflictException(ApiErrorCode.DDL_REVALIDATION_REQUIRED));
              }
              return revalidate(detail, current, identity).thenReturn(detail);
            })
        .flatMap(
            detail ->
                Mono.fromCallable(
                        () -> {
                          ApprovalRequest request = detail.request();
                          try {
                            if (!repository.submitWithResourceClaims(
                                request.tenantId(),
                                request.id(),
                                command.revision(),
                                List.copyOf(resourceKeys(request, detail.items())),
                                identity.userId(),
                                event(
                                    request,
                                    identity,
                                    "SUBMITTED",
                                    "DDL approval submitted",
                                    null))) {
                              throw new ConflictException(
                                  ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
                            }
                          } catch (DataIntegrityViolationException exception) {
                            throw new ConflictException(ApiErrorCode.APPROVAL_RESOURCE_CONFLICT);
                          }
                          return requireVisible(requestId, identity, false);
                        })
                    .subscribeOn(jdbcScheduler));
  }

  public Mono<ApprovalDetail> review(
      String requestId, ApprovalTransitionRequest command, boolean approve, Identity identity) {
    return Mono.fromCallable(
            () -> {
              requireAdmin(identity);
              validateRevision(command);
              if (!approve && isBlank(command.comment())) {
                throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
              }
              ApprovalDetail detail = requireVisible(requestId, identity, true);
              ApprovalRequest request = detail.request();
              ApprovalStatus target;
              if (!approve) {
                target = ApprovalStatus.REJECTED;
              } else if ("AUTO_AFTER_APPROVAL".equals(request.executionMode())) {
                target = ApprovalStatus.QUEUED;
              } else {
                target = ApprovalStatus.APPROVED;
              }
              transition(
                  request,
                  command.revision(),
                  ApprovalStatus.SUBMITTED,
                  target,
                  identity,
                  command.comment(),
                  target.name());
              return requireVisible(requestId, identity, true);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalDetail> interrupt(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    validateRevision(command);
    return Mono.fromCallable(
            () -> {
              ApprovalDetail detail = requireVisible(requestId, identity, false);
              ApprovalRequest request = detail.request();
              if (request.status() != ApprovalStatus.DRAFT
                  && request.status() != ApprovalStatus.SUBMITTED) {
                throw new ConflictException(ApiErrorCode.APPROVAL_INVALID_STATE);
              }
              transition(
                  request,
                  command.revision(),
                  request.status(),
                  ApprovalStatus.CANCELLED,
                  identity,
                  null,
                  "INTERRUPTED_BY_APPLICANT");
              return requireVisible(requestId, identity, false);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalDetail> updateSqlPlan(
      String requestId, ApprovalSqlPlanUpdateRequest command, Identity identity) {
    requireAdmin(identity);
    if (command == null || command.items() == null || command.items().isEmpty()) {
      return Mono.error(PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST));
    }
    return Mono.fromCallable(
            () -> {
              ApprovalDetail detail = requireVisible(requestId, identity, true);
              ApprovalRequest current = detail.request();
              if (current.status() != ApprovalStatus.DRAFT
                  && current.status() != ApprovalStatus.SUBMITTED) {
                throw new ConflictException(ApiErrorCode.APPROVAL_INVALID_STATE);
              }
              if (command.revision() != current.revision()
                  || command.items().size() != detail.items().size()) {
                throw new ConflictException(ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
              }
              Map<String, String> sqlById =
                  command.items().stream()
                      .collect(
                          java.util.stream.Collectors.toMap(
                              ApprovalSqlPlanUpdateRequest.Item::id,
                              item -> item.sqlText() == null ? "" : item.sqlText().trim()));
              List<ApprovalItem> updatedItems =
                  detail.items().stream().map(item -> updatedItem(item, sqlById)).toList();
              ObjectNode content = (ObjectNode) mapper.readTree(current.contentJson());
              content.put("manualSqlOverride", true);
              ArrayNode statements = (ArrayNode) content.withArray("statements");
              if (statements.size() != updatedItems.size()) {
                throw new ConflictException(ApiErrorCode.APPROVAL_CONTENT_CHANGED);
              }
              for (int index = 0; index < updatedItems.size(); index++) {
                ((ObjectNode) statements.get(index)).put("sql", updatedItems.get(index).sqlText());
              }
              String contentJson = mapper.writeValueAsString(content);
              String digest = canonicalJsonDigest(content);
              String planHash = computePlanHash(content.get("intent"), content.get("statements"));
              ApprovalRequest updatedRequest =
                  new ApprovalRequest(
                      current.id(),
                      current.tenantId(),
                      current.requestNo(),
                      current.workOrderTypeKey(),
                      current.workOrderTypeRevision(),
                      current.typeDefinitionChecksum(),
                      current.title(),
                      current.summary(),
                      current.applicantUserId(),
                      current.applicantDisplayName(),
                      current.sourceSessionId(),
                      current.sourceRunId(),
                      current.connectionId(),
                      current.connectionName(),
                      ApprovalStatus.DRAFT,
                      contentJson,
                      current.contentVersion() + 1,
                      digest,
                      current.executionMode(),
                      current.executionAttempt(),
                      null,
                      null,
                      null,
                      current.revision(),
                      current.createdAt(),
                      null,
                      null,
                      null,
                      null,
                      Instant.now(),
                      current.planVersion() + 1,
                      planHash,
                      current.envSnapshotJson(),
                      current.policyVersionRef());
              if (!repository.updateSqlPlan(
                  updatedRequest,
                  command.revision(),
                  current.status(),
                  updatedItems,
                  event(
                      current,
                      identity,
                      "SQL_PLAN_EDITED",
                      "SQL plan edited by administrator",
                      null))) {
                throw new ConflictException(ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
              }
              return requireVisible(requestId, identity, true);
            })
        .subscribeOn(jdbcScheduler);
  }

  private ApprovalItem updatedItem(ApprovalItem item, Map<String, String> sqlById) {
    try {
      String sql = sqlById.get(item.id());
      if (sql == null || sql.isBlank()) {
        throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
      }
      validateManualSql(item, sql);
      return new ApprovalItem(
          item.id(),
          item.tenantId(),
          item.requestId(),
          item.ordinal(),
          item.operationKind(),
          sql,
          sha256(normalizeSql(sql)),
          item.objectRefsJson(),
          item.riskLevel(),
          item.warningsJson(),
          item.idempotencyStrategy(),
          item.preconditionJson(),
          item.createdAt());
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to update approval SQL", exception);
    }
  }

  private void validateManualSql(ApprovalItem item, String sql) throws Exception {
    String withoutTrailingTerminator = sql.stripTrailing().replaceFirst(";$", "");
    if (withoutTrailingTerminator.contains(";")) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
    String normalized = normalizeSql(withoutTrailingTerminator).replace("`", "");
    String expectedPrefix =
        item.operationKind()
                == io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind
                    .CREATE_TABLE
            ? "CREATE TABLE "
            : "ALTER TABLE ";
    if (!normalized.toUpperCase(java.util.Locale.ROOT).startsWith(expectedPrefix)) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
    for (String objectRef : mapper.readValue(item.objectRefsJson(), String[].class)) {
      if (!normalized
          .toLowerCase(java.util.Locale.ROOT)
          .contains(objectRef.toLowerCase(java.util.Locale.ROOT))) {
        throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
      }
    }
  }

  public Mono<ApprovalDetail> execute(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    requireAdmin(identity);
    validateRevision(command);
    return Mono.fromCallable(() -> requireVisible(requestId, identity, true))
        .subscribeOn(jdbcScheduler)
        .flatMap(
            detail ->
                revalidate(detail, frozenDefinition(detail.request()), identity).thenReturn(detail))
        .flatMap(detail -> verifyEnvNotDrifted(detail, identity).thenReturn(detail))
        .flatMap(
            detail ->
                Mono.fromCallable(
                        () -> {
                          int attempt =
                              repository.beginExecution(
                                  identity.tenantId(),
                                  requestId,
                                  command.revision(),
                                  identity.userId(),
                                  event(
                                      detail.request(),
                                      identity,
                                      "EXECUTION_STARTED",
                                      "Manual DDL execution started",
                                      null));
                          if (attempt < 0) {
                            throw new ConflictException(
                                ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
                          }
                          return new ExecutionContext(detail, attempt);
                        })
                    .subscribeOn(jdbcScheduler))
        .flatMap(
            context ->
                Flux.fromIterable(context.detail().items())
                    .concatMap(
                        item ->
                            executeItem(
                                context.detail().request(), item, context.attempt(), identity))
                    .then(
                        Mono.fromCallable(
                                () -> {
                                  finishExecutionRequest(
                                      context.detail().request(), true, identity, null);
                                  return requireVisible(requestId, identity, true);
                                })
                            .subscribeOn(jdbcScheduler))
                    .onErrorResume(
                        ExecutionFailedException.class,
                        failure ->
                            Mono.fromCallable(
                                    () -> {
                                      finishExecutionRequest(
                                          context.detail().request(),
                                          false,
                                          identity,
                                          failure.getMessage());
                                      return requireVisible(requestId, identity, true);
                                    })
                                .subscribeOn(jdbcScheduler)));
  }

  public Mono<ApprovalDetail> retry(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    requireAdmin(identity);
    validateRevision(command);
    return Mono.fromCallable(() -> requireVisible(requestId, identity, true))
        .subscribeOn(jdbcScheduler)
        .flatMap(
            detail -> {
              ApprovalStatus status = detail.request().status();
              if (status != ApprovalStatus.FAILED && status != ApprovalStatus.RECONCILING) {
                return Mono.error(new ConflictException(ApiErrorCode.APPROVAL_INVALID_STATE));
              }
              return revalidate(detail, frozenDefinition(detail.request()), identity)
                  .thenReturn(detail);
            })
        .flatMap(detail -> verifyEnvNotDrifted(detail, identity).thenReturn(detail))
        .flatMap(
            detail ->
                Mono.fromCallable(
                        () -> {
                          int attempt =
                              repository.retryExecution(
                                  identity.tenantId(),
                                  requestId,
                                  command.revision(),
                                  identity.userId(),
                                  event(
                                      detail.request(),
                                      identity,
                                      "EXECUTION_STARTED",
                                      "Manual DDL retry started",
                                      null));
                          if (attempt < 0) {
                            throw new ConflictException(
                                ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
                          }
                          return new ExecutionContext(detail, attempt);
                        })
                    .subscribeOn(jdbcScheduler))
        .flatMap(
            context -> {
              Set<String> succeeded =
                  repository.findSucceededItemIds(context.detail().request().tenantId(), requestId);
              return Flux.fromIterable(context.detail().items())
                  .concatMap(
                      item -> {
                        if (succeeded.contains(item.id())) {
                          return Mono.<Void>fromRunnable(
                                  () ->
                                      repository.createSkippedExecution(
                                          context.detail().request().tenantId(),
                                          requestId,
                                          item.id(),
                                          context.attempt(),
                                          item.ordinal()))
                              .subscribeOn(jdbcScheduler);
                        }
                        return executeItem(
                            context.detail().request(), item, context.attempt(), identity);
                      })
                  .then(
                      Mono.fromCallable(
                              () -> {
                                finishExecutionRequest(
                                    context.detail().request(), true, identity, null);
                                return requireVisible(requestId, identity, true);
                              })
                          .subscribeOn(jdbcScheduler))
                  .onErrorResume(
                      ExecutionFailedException.class,
                      failure ->
                          Mono.fromCallable(
                                  () -> {
                                    finishExecutionRequest(
                                        context.detail().request(),
                                        false,
                                        identity,
                                        failure.getMessage());
                                    return requireVisible(requestId, identity, true);
                                  })
                              .subscribeOn(jdbcScheduler));
            });
  }

  /**
   * Drains claimable QUEUED work orders (AUTO_AFTER_APPROVAL) by claiming each via a CAS lease and
   * executing it under a per-tenant system identity. Invoked by {@link ApprovalExecutionWorker} on
   * a schedule. Lease (revision + execution_lease_until in the CAS WHERE) prevents double-execution
   * across worker instances; a failed claim (race) is skipped.
   */
  public Mono<Void> drainOnce() {
    return Mono.fromCallable(() -> repository.findClaimableQueuedRequests(DRAIN_BATCH))
        .subscribeOn(jdbcScheduler)
        .flatMapMany(Flux::fromIterable)
        .concatMap(this::drainOne)
        .then();
  }

  /**
   * Reclaims RUNNING work orders whose lease has expired (worker died mid-execution) by moving them
   * to RECONCILING for admin retry/close. Safe because a live worker renews the lease per item, so
   * only a genuinely stuck worker's lease expires; a CAS conflict (worker finished meanwhile) is
   * skipped.
   */
  public Mono<Void> reclaimStuck() {
    return Mono.fromCallable(repository::findStuckRunningRequests)
        .subscribeOn(jdbcScheduler)
        .flatMapMany(Flux::fromIterable)
        .concatMap(
            request -> {
              Identity system = systemIdentity(request.tenantId());
              return Mono.<Void>fromRunnable(
                      () ->
                          transition(
                              request,
                              request.revision(),
                              ApprovalStatus.RUNNING,
                              ApprovalStatus.RECONCILING,
                              system,
                              null,
                              "EXECUTION_STUCK_RECONCILING"))
                  .subscribeOn(jdbcScheduler)
                  .onErrorResume(exception -> Mono.empty());
            })
        .then();
  }

  /** Expires APPROVED/QUEUED work orders not progressed within the approval TTL (V3 P3). */
  public Mono<Void> expireStale() {
    java.time.Instant cutoff = java.time.Instant.now().minus(APPROVAL_EXPIRY);
    return Mono.fromCallable(() -> repository.findExpiredApprovalCandidates(cutoff))
        .subscribeOn(jdbcScheduler)
        .flatMapMany(Flux::fromIterable)
        .concatMap(
            request -> {
              Identity system = systemIdentity(request.tenantId());
              return Mono.<Void>fromRunnable(
                      () ->
                          transition(
                              request,
                              request.revision(),
                              request.status(),
                              ApprovalStatus.EXPIRED,
                              system,
                              null,
                              "APPROVAL_EXPIRED"))
                  .subscribeOn(jdbcScheduler)
                  .onErrorResume(exception -> Mono.empty());
            })
        .then();
  }

  private Mono<Void> drainOne(ApprovalRequest queued) {
    Identity system = systemIdentity(queued.tenantId());
    return Mono.fromCallable(() -> repository.findDetail(queued.tenantId(), queued.id()))
        .subscribeOn(jdbcScheduler)
        .flatMap(opt -> opt.map(Mono::just).orElse(Mono.empty()))
        .flatMap(
            detail ->
                revalidate(detail, frozenDefinition(detail.request()), system).thenReturn(detail))
        .flatMap(detail -> verifyEnvNotDrifted(detail, system).thenReturn(detail))
        .flatMap(
            detail ->
                Mono.fromCallable(
                        () -> {
                          int attempt =
                              repository.claimQueued(
                                  queued.tenantId(),
                                  queued.id(),
                                  queued.revision(),
                                  Instant.now().plus(EXECUTION_LEASE_DURATION),
                                  system.userId(),
                                  event(
                                      detail.request(),
                                      system,
                                      "EXECUTION_STARTED",
                                      "Auto DDL execution started",
                                      null));
                          return attempt < 0
                              ? Optional.<ExecutionContext>empty()
                              : Optional.of(new ExecutionContext(detail, attempt));
                        })
                    .subscribeOn(jdbcScheduler))
        .flatMap(
            opt ->
                opt.map(
                        context ->
                            Flux.fromIterable(context.detail().items())
                                .concatMap(
                                    item ->
                                        executeItem(
                                            context.detail().request(),
                                            item,
                                            context.attempt(),
                                            system))
                                .then(
                                    Mono.fromCallable(
                                            () -> {
                                              finishExecutionRequest(
                                                  context.detail().request(), true, system, null);
                                              return requireVisible(queued.id(), system, true);
                                            })
                                        .subscribeOn(jdbcScheduler))
                                .onErrorResume(
                                    ExecutionFailedException.class,
                                    failure ->
                                        Mono.fromCallable(
                                                () -> {
                                                  finishExecutionRequest(
                                                      context.detail().request(),
                                                      false,
                                                      system,
                                                      failure.getMessage());
                                                  return requireVisible(queued.id(), system, true);
                                                })
                                            .subscribeOn(jdbcScheduler))
                                .then())
                    .orElse(Mono.empty()));
  }

  private static Identity systemIdentity(String tenantId) {
    return new Identity(tenantId, "system", Set.of("ROLE_ADMIN"));
  }

  public Mono<ApprovalDetail> closeFailed(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    requireAdmin(identity);
    validateRevision(command);
    if (isBlank(command.comment())) {
      return Mono.error(PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST));
    }
    return Mono.fromCallable(
            () -> {
              ApprovalDetail detail = requireVisible(requestId, identity, true);
              ApprovalRequest request = detail.request();
              ApprovalStatus expected = request.status();
              if (expected != ApprovalStatus.FAILED && expected != ApprovalStatus.RECONCILING) {
                throw new ConflictException(ApiErrorCode.APPROVAL_INVALID_STATE);
              }
              repository.finishRequestExecution(
                  request.tenantId(),
                  request.id(),
                  command.revision(),
                  expected,
                  ApprovalStatus.CANCELLED,
                  identity.userId(),
                  event(
                      request,
                      identity,
                      "FAILED_EXECUTION_CLOSED",
                      "Failed DDL execution closed by administrator",
                      command.comment()));
              return requireVisible(requestId, identity, true);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalDetail> detail(String requestId, Identity identity) {
    return Mono.fromCallable(() -> requireVisible(requestId, identity, identity.isAdmin()))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalPageResponse> list(
      List<ApprovalStatus> statuses,
      String workOrderTypeKey,
      String applicant,
      String keyword,
      Instant createdFrom,
      Instant createdTo,
      int page,
      int pageSize,
      Identity identity) {
    int normalizedPage = Math.max(1, page);
    int normalizedPageSize = Math.max(1, Math.min(pageSize, 100));
    String visibleApplicant = identity.isAdmin() ? null : identity.userId();
    return Mono.fromCallable(
            () -> {
              long total =
                  repository.countRequests(
                      identity.tenantId(),
                      visibleApplicant,
                      statuses,
                      workOrderTypeKey,
                      applicant,
                      keyword,
                      createdFrom,
                      createdTo);
              List<ApprovalRequest> items =
                  repository.findRequests(
                      identity.tenantId(),
                      visibleApplicant,
                      statuses,
                      workOrderTypeKey,
                      applicant,
                      keyword,
                      createdFrom,
                      createdTo,
                      (normalizedPage - 1) * normalizedPageSize,
                      normalizedPageSize);
              return new ApprovalPageResponse(items, total, normalizedPage, normalizedPageSize);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<List<ApprovalExecution>> executions(String requestId, Identity identity) {
    requireAdmin(identity);
    return Mono.fromCallable(
            () -> {
              requireVisible(requestId, identity, true);
              return repository.findExecutions(identity.tenantId(), requestId);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<List<ApprovalNodeExecution>> nodeExecutions(
      String requestId,
      String executionId,
      String status,
      int offset,
      int limit,
      Identity identity) {
    requireAdmin(identity);
    return Mono.fromCallable(
            () -> {
              requireVisible(requestId, identity, true);
              boolean belongsToRequest =
                  repository.findExecutions(identity.tenantId(), requestId).stream()
                      .anyMatch(execution -> execution.id().equals(executionId));
              if (!belongsToRequest) {
                throw new NotFoundException("ApprovalExecution", executionId);
              }
              return repository.findNodeExecutions(
                  identity.tenantId(), executionId, trimToNull(status), offset, limit);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<List<ApprovalTypeDefinition>> listTypes(String connectionId, Identity identity) {
    return connections
        .findById(connectionId, identity)
        .thenReturn(catalog.listEnabled(identity.tenantId(), connectionId))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<List<ApprovalTypeDefinition>> listTypeDefinitions(Identity identity) {
    requireAdmin(identity);
    return Mono.fromCallable(() -> catalog.listAll(identity.tenantId())).subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalTypeDefinition> updateTypeDefinition(
      String typeKey, ApprovalTypeUpdateRequest command, Identity identity) {
    requireAdmin(identity);
    return Mono.fromCallable(
            () -> catalog.update(identity.tenantId(), typeKey, command, identity.userId()))
        .subscribeOn(jdbcScheduler);
  }

  private DdlApprovalPrepareResponse createDraft(
      DdlApprovalPrepareRequest command,
      Identity identity,
      ClickHouseConnectionResponse connection,
      ApprovalTypeDefinition definition,
      DdlSchemaSnapshot schema,
      CompiledDdlPlan plan) {
    try {
      Instant now = Instant.now();
      String idempotencyKey = prepareIdempotencyKey(command);
      if (idempotencyKey != null) {
        var replay =
            repository.findDetailByIdempotencyKey(
                identity.tenantId(), identity.userId(), idempotencyKey);
        if (replay.isPresent()) return preparedResponse(replay.get());
      }
      ApprovalDetail current =
          isBlank(command.draftId()) ? null : requireVisible(command.draftId(), identity, false);
      if (current != null && command.expectedRevision() == null) {
        throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
      }
      String requestId = current == null ? Ulid.next() : current.request().id();
      String requestNo =
          current == null
              ? "DDL-" + REQUEST_DATE.format(now) + "-" + Ulid.next()
              : current.request().requestNo();
      ObjectNode content = mapper.createObjectNode();
      content.put("connectionId", command.connectionId());
      content.put("connectionName", connection.name());
      content.put("workOrderTypeKey", definition.typeKey());
      content.put("workOrderTypeRevision", definition.definitionRevision());
      content.put("generationRuleChecksum", definition.checksum());
      content.put("generatorKey", definition.generatorKey());
      String executionMode = executionMode(definition);
      content.put("executionMode", executionMode);
      content.set("intent", command.intent());
      content.set("generationRule", mapper.readTree(definition.generationRuleJson()));
      content.set("statements", mapper.valueToTree(plan.statements()));
      content.set("ruleSummaries", mapper.valueToTree(plan.ruleSummaries()));
      String contentJson = mapper.writeValueAsString(content);
      String digest = canonicalJsonDigest(content);
      String planHash = computePlanHash(command.intent(), content.get("statements"));
      String envSnapshotJson = envSnapshot(command.connectionId(), definition, schema);
      String policyVersionRef = definition.definitionRevision() + ":v1";
      int planVersion =
          current == null
              ? 1
              : (planHash.equals(current.request().planHash())
                  ? current.request().planVersion()
                  : current.request().planVersion() + 1);
      ApprovalRequest request =
          new ApprovalRequest(
              requestId,
              identity.tenantId(),
              requestNo,
              definition.typeKey(),
              definition.definitionRevision(),
              definition.checksum(),
              command.title().trim(),
              trimToNull(command.summary()),
              identity.userId(),
              applicantDisplayName(identity),
              trimToNull(command.sourceSessionId()),
              trimToNull(command.sourceRunId()),
              command.connectionId(),
              connection.name(),
              ApprovalStatus.DRAFT,
              contentJson,
              current == null ? 1 : current.request().contentVersion() + 1,
              digest,
              executionMode,
              0,
              null,
              null,
              null,
              current == null ? 0 : current.request().revision(),
              current == null ? now : current.request().createdAt(),
              null,
              null,
              null,
              null,
              now,
              planVersion,
              planHash,
              envSnapshotJson,
              policyVersionRef);
      List<ApprovalItem> items =
          plan.statements().stream()
              .map(statement -> toItem(requestId, identity.tenantId(), statement, now))
              .toList();
      try {
        if (current == null) {
          repository.createDraft(
              request,
              items,
              idempotencyKey,
              event(request, identity, "DRAFT_CREATED", "DDL approval draft created", null));
        } else if (!repository.updateDraft(
            request,
            command.expectedRevision(),
            items,
            idempotencyKey,
            event(request, identity, "DRAFT_UPDATED", "DDL approval draft updated", null))) {
          throw new ConflictException(ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
        }
      } catch (DataIntegrityViolationException exception) {
        if (idempotencyKey == null) throw exception;
        return repository
            .findDetailByIdempotencyKey(identity.tenantId(), identity.userId(), idempotencyKey)
            .map(this::preparedResponse)
            .orElseThrow(() -> exception);
      }
      if (current != null) {
        return preparedResponse(
            repository
                .findDetail(identity.tenantId(), request.id())
                .orElseThrow(() -> new NotFoundException("ApprovalRequest", request.id())));
      }
      return preparedResponse(new ApprovalDetail(request, items, List.of()));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create approval content snapshot", exception);
    }
  }

  private DdlApprovalPrepareResponse preparedResponse(ApprovalDetail detail) {
    try {
      JsonNode content = mapper.readTree(detail.request().contentJson());
      List<String> summaries =
          mapper.convertValue(
              content.path("ruleSummaries"),
              mapper.getTypeFactory().constructCollectionType(List.class, String.class));
      List<DdlApprovalPrepareResponse.PreparedStatement> statements =
          detail.items().stream()
              .map(
                  item -> {
                    try {
                      List<String> warnings =
                          mapper.readValue(
                              item.warningsJson(),
                              mapper
                                  .getTypeFactory()
                                  .constructCollectionType(List.class, String.class));
                      return new DdlApprovalPrepareResponse.PreparedStatement(
                          item.ordinal(),
                          item.operationKind().name(),
                          item.sqlText(),
                          item.riskLevel(),
                          warnings);
                    } catch (Exception exception) {
                      throw new IllegalStateException(
                          "Unable to read frozen DDL warnings", exception);
                    }
                  })
              .toList();
      return new DdlApprovalPrepareResponse(
          detail.request().id(),
          detail.request().requestNo(),
          detail.request().revision(),
          detail.request().contentDigest(),
          detail.request().planVersion(),
          detail.request().planHash(),
          statements,
          summaries,
          true);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read approval content snapshot", exception);
    }
  }

  private String prepareIdempotencyKey(DdlApprovalPrepareRequest command) throws Exception {
    if (isBlank(command.sourceRunId())) return null;
    ObjectNode canonical = mapper.createObjectNode();
    canonical.put("sourceRunId", command.sourceRunId().trim());
    canonical.put("draftId", trimToNull(command.draftId()));
    if (command.expectedRevision() != null) {
      canonical.put("expectedRevision", command.expectedRevision());
    }
    canonical.put("connectionId", command.connectionId().trim());
    canonical.put("workOrderTypeKey", command.workOrderTypeKey().trim());
    canonical.put("title", command.title().trim());
    canonical.put("summary", trimToNull(command.summary()));
    canonical.set("intent", command.intent());
    return "prepare:" + sha256(mapper.writeValueAsString(canonical));
  }

  private String applicantDisplayName(Identity identity) {
    return users
        .findByTenantIdAndUserId(identity.tenantId(), identity.userId())
        .map(account -> trimToNull(account.username()))
        .orElse(identity.userId());
  }

  private ApprovalItem toItem(
      String requestId, String tenantId, CompiledDdlStatement statement, Instant now) {
    try {
      return new ApprovalItem(
          Ulid.next(),
          tenantId,
          requestId,
          statement.ordinal(),
          statement.operationKind(),
          statement.sql(),
          sha256(normalizeSql(statement.sql())),
          mapper.writeValueAsString(statement.objectRefs()),
          statement.riskLevel(),
          mapper.writeValueAsString(statement.warnings()),
          statement.idempotencyStrategy(),
          null,
          now);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to persist compiled DDL item", exception);
    }
  }

  private Mono<Void> executeItem(
      ApprovalRequest request, ApprovalItem item, int attempt, Identity identity) {
    String queryId = "approval-" + request.id() + "-" + attempt + "-" + item.ordinal();
    return Mono.fromCallable(
            () -> {
              repository.renewExecutionLease(
                  request.tenantId(), request.id(), Instant.now().plus(EXECUTION_LEASE_DURATION));
              return repository.createExecution(
                  request.tenantId(), request.id(), item.id(), attempt, item.ordinal(), queryId);
            })
        .subscribeOn(jdbcScheduler)
        .flatMap(
            executionId ->
                executeStatement(request, item, identity, queryId, executionId)
                    .onErrorResume(ExecutionFailedException.class, Mono::error)
                    .onErrorResume(
                        exception ->
                            finishFailedExecution(request, item, executionId, null, 0, exception)));
  }

  /**
   * Sends the frozen DDL, then records per-node results from {@code system.distributed_ddl_queue}
   * (real cluster view). Falls back to a single local node when no per-host rows are available
   * (non-ON-CLUSTER, read mismatch, or permission). Statement success is authoritative via the
   * query exception; node rows are observability.
   */
  private Mono<Void> executeStatement(
      ApprovalRequest request,
      ApprovalItem item,
      Identity identity,
      String queryId,
      String executionId) {
    long startedNanos = System.nanoTime();
    Instant started = Instant.now();
    return connections
        .query(request.connectionId(), item.sqlText(), Map.of("query_id", queryId), identity)
        .then(
            Mono.<Void>fromCallable(
                    () -> {
                      long duration = elapsedMillis(startedNanos);
                      List<DdlSchemaInspector.NodeStatus> statuses;
                      try {
                        statuses =
                            schemaInspector
                                .nodeStatuses(
                                    request.connectionId(),
                                    objectMarker(item),
                                    started.minusSeconds(5),
                                    identity)
                                .block();
                      } catch (RuntimeException exception) {
                        statuses = List.of();
                      }
                      ClickHouseConnectionResponse connection =
                          connections.findById(request.connectionId(), identity).block();
                      recordNodeResults(
                          request,
                          executionId,
                          connection,
                          statuses == null ? List.of() : statuses,
                          duration);
                      repository.finishExecution(
                          request.tenantId(), executionId, true, duration, null, null);
                      return (Void) null;
                    })
                .subscribeOn(jdbcScheduler))
        .onErrorResume(
            exception ->
                finishFailedExecution(
                    request, item, executionId, null, elapsedMillis(startedNanos), exception));
  }

  /**
   * Stable marker (target object name) for correlating a DDL with its distributed_ddl_queue rows.
   */
  private String objectMarker(ApprovalItem item) {
    try {
      JsonNode refs = mapper.readTree(item.objectRefsJson());
      if (refs.isArray() && !refs.isEmpty()) {
        return refs.path(0).asText("");
      }
    } catch (Exception ignored) {
      // blank marker -> caller falls back to single local node
    }
    return "";
  }

  private void recordNodeResults(
      ApprovalRequest request,
      String executionId,
      ClickHouseConnectionResponse connection,
      List<DdlSchemaInspector.NodeStatus> statuses,
      long duration) {

    if (statuses.isEmpty()) {
      Endpoint endpoint = endpoint(connection.url());
      String nodeId =
          repository.createNodeExecution(
              request.tenantId(), executionId, endpoint.key(), endpoint.host(), endpoint.port());
      repository.finishNodeExecution(request.tenantId(), nodeId, true, duration, null, null);
    } else {
      for (DdlSchemaInspector.NodeStatus status : statuses) {
        String nodeKey = status.host() + ":" + status.port();
        String nodeId =
            repository.createNodeExecution(
                request.tenantId(), executionId, nodeKey, status.host(), status.port());
        repository.finishNodeExecution(
            request.tenantId(),
            nodeId,
            status.succeeded(),
            status.durationMs(),
            status.errorCode(),
            status.message());
      }
    }
  }

  private Mono<Void> finishFailedExecution(
      ApprovalRequest request,
      ApprovalItem item,
      String executionId,
      String nodeExecutionId,
      long duration,
      Throwable exception) {
    return Mono.<Void>fromRunnable(
            () -> {
              String code = exception.getClass().getSimpleName();
              String message = "ClickHouse rejected approval item #" + item.ordinal();
              if (nodeExecutionId != null) {
                repository.finishNodeExecution(
                    request.tenantId(), nodeExecutionId, false, duration, code, message);
              }
              repository.finishExecution(
                  request.tenantId(), executionId, false, duration, code, message);
            })
        .subscribeOn(jdbcScheduler)
        .then(Mono.error(new ExecutionFailedException("DDL item #" + item.ordinal() + " failed")));
  }

  private static Endpoint endpoint(String url) {
    try {
      URI uri = URI.create(url);
      int port = uri.getPort();
      if (port < 0) port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
      return new Endpoint(uri.getHost() + ":" + port, uri.getHost(), port);
    } catch (RuntimeException exception) {
      return new Endpoint(url, url, null);
    }
  }

  private void finishExecutionRequest(
      ApprovalRequest request, boolean succeeded, Identity identity, String detail) {
    ApprovalStatus target = succeeded ? ApprovalStatus.SUCCEEDED : ApprovalStatus.FAILED;
    repository.finishRequestExecution(
        request.tenantId(),
        request.id(),
        request.revision() + 1,
        ApprovalStatus.RUNNING,
        target,
        identity.userId(),
        event(
            request,
            identity,
            "EXECUTION_" + target.name(),
            succeeded ? "DDL execution succeeded" : "DDL execution failed",
            detail));
  }

  private Set<String> resourceKeys(ApprovalRequest request, List<ApprovalItem> items) {
    try {
      Set<String> keys = new LinkedHashSet<>();
      for (ApprovalItem item : items) {
        for (String objectRef : mapper.readValue(item.objectRefsJson(), String[].class)) {
          keys.add("clickhouse/" + request.connectionId() + "/" + objectRef.toLowerCase());
        }
      }
      return keys;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to read frozen DDL resource references", exception);
    }
  }

  private Mono<DdlSchemaSnapshot> schema(DdlApprovalPrepareRequest command, Identity identity) {
    if ("CLICKHOUSE_CREATE_TABLE".equals(command.workOrderTypeKey())) {
      return Mono.just(DdlSchemaSnapshot.EMPTY);
    }
    String database = command.intent().path("database").asText("").trim();
    String table = command.intent().path("table").asText("").trim();
    if (database.isEmpty() || table.isEmpty()) {
      return Mono.error(PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST));
    }
    return schemaInspector.inspect(command.connectionId(), database, table, identity);
  }

  /**
   * Execute-time environment drift check (V3 P2). Consumes the env_snapshot frozen at prepare: for
   * ALTER-type plans the snapshot holds the target table schema; if it has changed by execute time
   * the approval rested on stale facts, so execution is blocked (re-prepare required). CREATE_TABLE
   * short-circuited to an empty snapshot at prepare, so there is no baseline to drift-check.
   */
  private Mono<Void> verifyEnvNotDrifted(ApprovalDetail detail, Identity identity) {
    String envJson = detail.request().envSnapshotJson();
    if (isBlank(envJson)) return Mono.empty();
    try {
      JsonNode env = mapper.readTree(envJson);
      JsonNode frozenColumns = env.path("schema").path("columns");
      if (!frozenColumns.isArray() || frozenColumns.isEmpty()) {
        return Mono.empty();
      }
      JsonNode intent = mapper.readTree(detail.request().contentJson()).path("intent");
      String database = intent.path("database").asText();
      String table = intent.path("table").asText();
      if (isBlank(database) || isBlank(table)) return Mono.empty();
      Set<String> frozenColumnSet = jsonToStringSet(frozenColumns);
      Set<String> frozenProtectedSet = jsonToStringSet(env.path("schema").path("protectedColumns"));
      return schemaInspector
          .inspect(detail.request().connectionId(), database, table, identity)
          .map(
              current -> {
                if (!current.columns().equals(frozenColumnSet)
                    || !current.protectedColumns().equals(frozenProtectedSet)) {
                  throw new ConflictException(ApiErrorCode.DDL_REVALIDATION_REQUIRED);
                }
                return current;
              })
          .then();
    } catch (RuntimeException exception) {
      return Mono.error(exception);
    } catch (Exception exception) {
      return Mono.error(new ConflictException(ApiErrorCode.DDL_REVALIDATION_REQUIRED));
    }
  }

  private Set<String> jsonToStringSet(JsonNode array) {
    Set<String> values = new TreeSet<>();
    if (array.isArray()) {
      array.forEach(node -> values.add(node.asText()));
    }
    return values;
  }

  private Mono<Void> revalidate(
      ApprovalDetail detail, ApprovalTypeDefinition definition, Identity identity) {
    try {
      if (!canonicalJsonDigest(mapper.readTree(detail.request().contentJson()))
          .equals(detail.request().contentDigest())) {
        return Mono.error(new ConflictException(ApiErrorCode.APPROVAL_CONTENT_CHANGED));
      }
      JsonNode content = mapper.readTree(detail.request().contentJson());
      if (content.path("manualSqlOverride").asBoolean(false)) {
        return manualPlanMatches(content.path("statements"), detail.items())
            ? Mono.empty()
            : Mono.error(new ConflictException(ApiErrorCode.APPROVAL_CONTENT_CHANGED));
      }
      JsonNode intent = content.path("intent");
      String typeKey = content.path("workOrderTypeKey").asText();
      if (!detail.request().workOrderTypeKey().equals(typeKey)
          || !detail
              .request()
              .typeDefinitionChecksum()
              .equals(content.path("generationRuleChecksum").asText())) {
        return Mono.error(new ConflictException(ApiErrorCode.APPROVAL_CONTENT_CHANGED));
      }
      DdlApprovalPrepareRequest frozenCommand =
          new DdlApprovalPrepareRequest(
              detail.request().connectionId(),
              typeKey,
              detail.request().title(),
              null,
              intent,
              null,
              null,
              null,
              null);
      return schema(frozenCommand, identity)
          .map(schema -> compiler.compile(intent, definition, schema))
          .flatMap(
              plan ->
                  frozenPlanMatches(plan, detail.items())
                      ? Mono.empty()
                      : Mono.error(new ConflictException(ApiErrorCode.DDL_REVALIDATION_REQUIRED)));
    } catch (RuntimeException exception) {
      return Mono.error(exception);
    } catch (Exception exception) {
      return Mono.error(new ConflictException(ApiErrorCode.DDL_REVALIDATION_REQUIRED));
    }
  }

  private ApprovalTypeDefinition frozenDefinition(ApprovalRequest request) {
    try {
      JsonNode content = mapper.readTree(request.contentJson());
      String generatorKey = content.path("generatorKey").asText();
      JsonNode generationRule = content.path("generationRule");
      if (generatorKey.isBlank() || !generationRule.isObject()) {
        throw new ConflictException(ApiErrorCode.DDL_REVALIDATION_REQUIRED);
      }
      return new ApprovalTypeDefinition(
          "frozen-" + request.id(),
          request.tenantId(),
          request.workOrderTypeKey(),
          "CLICKHOUSE_DDL",
          "{}",
          "{}",
          generatorKey,
          "[]",
          mapper.writeValueAsString(generationRule),
          null,
          "{}",
          "FROZEN",
          request.workOrderTypeRevision(),
          request.typeDefinitionChecksum(),
          request.applicantUserId(),
          request.applicantUserId(),
          null,
          request.createdAt(),
          request.updatedAt(),
          null);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ConflictException(ApiErrorCode.DDL_REVALIDATION_REQUIRED);
    }
  }

  private static boolean frozenPlanMatches(CompiledDdlPlan plan, List<ApprovalItem> frozenItems) {
    if (plan.statements().size() != frozenItems.size()) return false;
    for (int index = 0; index < frozenItems.size(); index++) {
      CompiledDdlStatement compiled = plan.statements().get(index);
      ApprovalItem frozen = frozenItems.get(index);
      if (compiled.ordinal() != frozen.ordinal()
          || compiled.operationKind() != frozen.operationKind()
          || !normalizeSql(compiled.sql()).equals(normalizeSql(frozen.sqlText()))) {
        return false;
      }
    }
    return true;
  }

  private static boolean manualPlanMatches(JsonNode statements, List<ApprovalItem> items) {
    if (!statements.isArray() || statements.size() != items.size()) return false;
    for (int index = 0; index < items.size(); index++) {
      if (!normalizeSql(statements.get(index).path("sql").asText())
          .equals(normalizeSql(items.get(index).sqlText()))) return false;
    }
    return true;
  }

  private ApprovalDetail requireVisible(String requestId, Identity identity, boolean adminAccess) {
    ApprovalDetail detail =
        repository
            .findDetail(identity.tenantId(), requestId)
            .orElseThrow(() -> new NotFoundException("ApprovalRequest", requestId));
    if (!adminAccess && !detail.request().applicantUserId().equals(identity.userId())) {
      throw new NotFoundException("ApprovalRequest", requestId);
    }
    return detail;
  }

  private void transition(
      ApprovalRequest request,
      long expectedRevision,
      ApprovalStatus expectedStatus,
      ApprovalStatus targetStatus,
      Identity actor,
      String comment,
      String eventType) {
    if (!repository.transition(
        request.tenantId(),
        request.id(),
        expectedRevision,
        expectedStatus,
        targetStatus,
        targetStatus == ApprovalStatus.SUBMITTED || targetStatus == ApprovalStatus.CANCELLED
            ? null
            : actor.userId(),
        targetStatus == ApprovalStatus.SUBMITTED || targetStatus == ApprovalStatus.CANCELLED
            ? null
            : actor.userId(),
        comment,
        event(request, actor, eventType, comment, null))) {
      throw new ConflictException(ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
    }
  }

  private static ApprovalEvent event(
      ApprovalRequest request,
      Identity actor,
      String type,
      String safeMessage,
      String detailsJson) {
    return new ApprovalEvent(
        Ulid.next(),
        request.tenantId(),
        request.id(),
        type,
        actor.userId(),
        actor.userId(),
        safeMessage,
        detailsJson,
        Instant.now());
  }

  private static void validatePrepare(DdlApprovalPrepareRequest command) {
    if (command == null
        || isBlank(command.connectionId())
        || isBlank(command.workOrderTypeKey())
        || isBlank(command.title())
        || command.intent() == null
        || !command.intent().isObject()
        || (isBlank(command.draftId()) != (command.expectedRevision() == null))
        || (command.expectedRevision() != null && command.expectedRevision() < 0)) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
  }

  private static void validateTransition(ApprovalTransitionRequest command) {
    validateRevision(command);
    if (isBlank(command.contentDigest())) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
  }

  private static void validateRevision(ApprovalTransitionRequest command) {
    if (command == null || command.revision() < 0) {
      throw PlainTextException.badRequest(ApiErrorCode.INVALID_REQUEST);
    }
  }

  private static long elapsedMillis(long started) {
    return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
  }

  private record ExecutionContext(ApprovalDetail detail, int attempt) {}

  private record Endpoint(String key, String host, Integer port) {}

  private static final class ExecutionFailedException extends RuntimeException {
    private ExecutionFailedException(String message) {
      super(message);
    }
  }

  private static void requireAdmin(Identity identity) {
    if (!identity.isAdmin()) {
      throw new AdminAccessRequiredException();
    }
  }

  private static String normalizeSql(String sql) {
    return sql.trim().replaceAll("\\s+", " ");
  }

  private static String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Hash JSON by value rather than by its database representation. MySQL's JSON type may reorder
   * object properties and add whitespace when a document is read back, while arrays retain their
   * business-significant order.
   */
  private String canonicalJsonDigest(JsonNode value) throws Exception {
    return sha256(mapper.writeValueAsString(canonicalJson(value)));
  }

  /**
   * Execution mode comes from the type's risk_policy_json: default {@code MANUAL_TRIGGER}; {@code
   * AUTO_AFTER_APPROVAL} opts a type into async execution via the worker.
   */
  private String executionMode(ApprovalTypeDefinition definition) {
    try {
      JsonNode policy = mapper.readTree(definition.riskPolicyJson());
      return "AUTO_AFTER_APPROVAL".equals(policy.path("executionMode").asText(""))
          ? "AUTO_AFTER_APPROVAL"
          : "MANUAL_TRIGGER";
    } catch (Exception exception) {
      return "MANUAL_TRIGGER";
    }
  }

  /**
   * Semantic content hash of the Plan. Drives the change classifier: an edit that changes plan_hash
   * invalidates approval; runtime-only edits (not represented here) do not. Excludes raw SQL (uses
   * its normalized digest), per-statement risk/warnings (derived), and the env/policy anchors
   * (captured separately). Reuses canonicalJsonDigest for a stable canonical encoding.
   */
  private String computePlanHash(JsonNode intent, JsonNode statements) throws Exception {
    ObjectNode semantic = mapper.createObjectNode();
    semantic.set("intent", intent);
    ArrayNode semanticStatements = mapper.createArrayNode();
    for (JsonNode statement : statements) {
      ObjectNode semanticStatement = mapper.createObjectNode();
      semanticStatement.put("ordinal", statement.path("ordinal").asInt());
      semanticStatement.put("operationKind", statement.path("operationKind").asText());
      semanticStatement.put("digest", sha256(normalizeSql(statement.path("sql").asText())));
      java.util.TreeSet<String> objectRefs = new java.util.TreeSet<>();
      statement.path("objectRefs").forEach(ref -> objectRefs.add(ref.asText()));
      ArrayNode refs = mapper.createArrayNode();
      objectRefs.forEach(refs::add);
      semanticStatement.set("objectRefs", refs);
      semanticStatement.put("idempotency", statement.path("idempotencyStrategy").asText());
      semanticStatements.add(semanticStatement);
    }
    semantic.set("statements", semanticStatements);
    return canonicalJsonDigest(semantic);
  }

  /** Captures the environment facts the Plan was validated against; drift-detected in P2. */
  private String envSnapshot(
      String connectionId, ApprovalTypeDefinition definition, DdlSchemaSnapshot schema)
      throws Exception {
    ObjectNode snapshot = mapper.createObjectNode();
    snapshot.put("connectionId", connectionId);
    snapshot.put("workOrderTypeKey", definition.typeKey());
    ObjectNode schemaNode = mapper.createObjectNode();
    ArrayNode columns = mapper.createArrayNode();
    schema.columns().stream().sorted().forEach(columns::add);
    ArrayNode protectedColumns = mapper.createArrayNode();
    schema.protectedColumns().stream().sorted().forEach(protectedColumns::add);
    schemaNode.set("columns", columns);
    schemaNode.set("protectedColumns", protectedColumns);
    snapshot.set("schema", schemaNode);
    return mapper.writeValueAsString(snapshot);
  }

  private JsonNode canonicalJson(JsonNode value) {
    if (value.isObject()) {
      ObjectNode canonical = mapper.createObjectNode();
      Set<String> names = new TreeSet<>();
      value.fieldNames().forEachRemaining(names::add);
      names.forEach(name -> canonical.set(name, canonicalJson(value.get(name))));
      return canonical;
    }
    if (value.isArray()) {
      ArrayNode canonical = mapper.createArrayNode();
      value.forEach(element -> canonical.add(canonicalJson(element)));
      return canonical;
    }
    return value;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String trimToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }
}
