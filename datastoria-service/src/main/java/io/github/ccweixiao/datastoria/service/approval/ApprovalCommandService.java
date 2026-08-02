package io.github.ccweixiao.datastoria.service.approval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalEvent;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionResponse;
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
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

@Service
public class ApprovalCommandService {

  private static final DateTimeFormatter REQUEST_DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

  private final ApprovalRepository repository;
  private final DdlWorkOrderTypeCatalog catalog;
  private final DdlPlanCompiler compiler;
  private final DdlSchemaInspector schemaInspector;
  private final ClickHouseConnectionService connections;
  private final ObjectMapper mapper;
  private final Scheduler jdbcScheduler;

  public ApprovalCommandService(
      ApprovalRepository repository,
      DdlWorkOrderTypeCatalog catalog,
      DdlPlanCompiler compiler,
      DdlSchemaInspector schemaInspector,
      ClickHouseConnectionService connections,
      ObjectMapper mapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.repository = repository;
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
                                compiler.compile(command.intent(), definition, schema))))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalDetail> submit(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    return Mono.fromCallable(
            () -> {
              validateTransition(command);
              ApprovalDetail detail = requireVisible(requestId, identity, false);
              ApprovalRequest request = detail.request();
              if (!request.contentDigest().equals(command.contentDigest())) {
                throw new ConflictException(ApiErrorCode.APPROVAL_CONTENT_CHANGED);
              }
              try {
                if (!repository.submitWithResourceClaims(
                    request.tenantId(),
                    request.id(),
                    command.revision(),
                    List.copyOf(resourceKeys(request, detail.items())),
                    identity.userId(),
                    event(request, identity, "SUBMITTED", "DDL approval submitted", null))) {
                  throw new ConflictException(ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
                }
              } catch (DataIntegrityViolationException exception) {
                throw new ConflictException(ApiErrorCode.APPROVAL_RESOURCE_CONFLICT);
              }
              return requireVisible(requestId, identity, false);
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<ApprovalDetail> review(
      String requestId, ApprovalTransitionRequest command, boolean approve, Identity identity) {
    return Mono.fromCallable(
            () -> {
              requireAdmin(identity);
              ApprovalDetail detail = requireVisible(requestId, identity, true);
              ApprovalRequest request = detail.request();
              if (request.applicantUserId().equals(identity.userId())) {
                throw new ConflictException(ApiErrorCode.APPROVAL_SELF_REVIEW_NOT_ALLOWED);
              }
              ApprovalStatus target = approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED;
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

  public Mono<ApprovalDetail> execute(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    requireAdmin(identity);
    validateRevision(command);
    return Mono.fromCallable(
            () -> {
              ApprovalDetail detail = requireVisible(requestId, identity, true);
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
                throw new ConflictException(ApiErrorCode.APPROVAL_DRAFT_REVISION_CONFLICT);
              }
              return new ExecutionContext(detail, attempt);
            })
        .subscribeOn(jdbcScheduler)
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

  public Mono<ApprovalDetail> closeFailed(
      String requestId, ApprovalTransitionRequest command, Identity identity) {
    requireAdmin(identity);
    validateRevision(command);
    return Mono.fromCallable(
            () -> {
              ApprovalDetail detail = requireVisible(requestId, identity, true);
              ApprovalRequest request = detail.request();
              repository.finishRequestExecution(
                  request.tenantId(),
                  request.id(),
                  command.revision(),
                  ApprovalStatus.FAILED,
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

  public Mono<List<ApprovalRequest>> list(ApprovalStatus status, int limit, Identity identity) {
    return Mono.fromCallable(
            () ->
                repository.findRequests(
                    identity.tenantId(),
                    identity.isAdmin() ? null : identity.userId(),
                    status,
                    limit))
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
      CompiledDdlPlan plan) {
    try {
      Instant now = Instant.now();
      String requestId = Ulid.next();
      String requestNo = "DDL-" + REQUEST_DATE.format(now) + "-" + Ulid.next();
      ObjectNode content = mapper.createObjectNode();
      content.put("connectionId", command.connectionId());
      content.put("connectionName", connection.name());
      content.put("workOrderTypeKey", definition.typeKey());
      content.put("workOrderTypeRevision", definition.definitionRevision());
      content.put("generationRuleChecksum", definition.checksum());
      content.put("executionMode", "MANUAL_TRIGGER");
      content.set("intent", command.intent());
      content.set("generationRule", mapper.readTree(definition.generationRuleJson()));
      content.set("statements", mapper.valueToTree(plan.statements()));
      String contentJson = mapper.writeValueAsString(content);
      String digest = sha256(contentJson);
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
              identity.userId(),
              trimToNull(command.sourceSessionId()),
              trimToNull(command.sourceRunId()),
              command.connectionId(),
              connection.name(),
              ApprovalStatus.DRAFT,
              contentJson,
              1,
              digest,
              "MANUAL_TRIGGER",
              0,
              null,
              null,
              null,
              0,
              now,
              null,
              null,
              null,
              null,
              now);
      List<ApprovalItem> items =
          plan.statements().stream()
              .map(statement -> toItem(requestId, identity.tenantId(), statement, now))
              .toList();
      repository.createDraft(
          request,
          items,
          event(request, identity, "DRAFT_CREATED", "DDL approval draft created", null));
      return new DdlApprovalPrepareResponse(
          requestId,
          requestNo,
          0,
          digest,
          plan.statements().stream()
              .map(
                  statement ->
                      new DdlApprovalPrepareResponse.PreparedStatement(
                          statement.ordinal(),
                          statement.operationKind().name(),
                          statement.sql(),
                          statement.riskLevel(),
                          statement.warnings()))
              .toList(),
          plan.ruleSummaries(),
          true);
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to create approval content snapshot", exception);
    }
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
            () ->
                repository.createExecution(
                    request.tenantId(), request.id(), item.id(), attempt, item.ordinal(), queryId))
        .subscribeOn(jdbcScheduler)
        .flatMap(
            executionId -> {
              long started = System.nanoTime();
              return connections
                  .query(
                      request.connectionId(), item.sqlText(), Map.of("query_id", queryId), identity)
                  .then(
                      Mono.<Void>fromRunnable(
                              () ->
                                  repository.finishExecution(
                                      request.tenantId(),
                                      executionId,
                                      true,
                                      elapsedMillis(started),
                                      null,
                                      null))
                          .subscribeOn(jdbcScheduler))
                  .onErrorResume(
                      exception ->
                          Mono.<Void>fromRunnable(
                                  () ->
                                      repository.finishExecution(
                                          request.tenantId(),
                                          executionId,
                                          false,
                                          elapsedMillis(started),
                                          exception.getClass().getSimpleName(),
                                          "ClickHouse rejected approval item #" + item.ordinal()))
                              .subscribeOn(jdbcScheduler)
                              .then(
                                  Mono.<Void>error(
                                      new ExecutionFailedException(
                                          "DDL item #" + item.ordinal() + " failed"))));
            });
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
        targetStatus == ApprovalStatus.SUBMITTED ? null : actor.userId(),
        targetStatus == ApprovalStatus.SUBMITTED ? null : actor.userId(),
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
        || !command.intent().isObject()) {
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

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String trimToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }
}
