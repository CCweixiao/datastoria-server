package io.github.ccweixiao.datastoria.service.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalTypeDefinition;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionResponse;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTransitionRequest;
import io.github.ccweixiao.datastoria.common.dto.approval.DdlApprovalPrepareRequest;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ApprovalRepository;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ApprovalCommandServiceTest {

  private static final Identity ADMIN = new Identity("tenant", "admin", Set.of("ROLE_ADMIN"));

  @Test
  void administratorCanApproveOwnSubmittedRequest() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ApprovalDetail submitted = detail(ApprovalStatus.SUBMITTED, "admin", 3);
    when(repository.findDetail("tenant", "request")).thenReturn(Optional.of(submitted));
    when(repository.transition(
            eq("tenant"),
            eq("request"),
            eq(3L),
            eq(ApprovalStatus.SUBMITTED),
            eq(ApprovalStatus.APPROVED),
            eq("admin"),
            eq("admin"),
            eq("approved"),
            any()))
        .thenReturn(true);
    ApprovalCommandService service =
        service(
            repository,
            mock(DdlWorkOrderTypeCatalog.class),
            mock(DdlPlanCompiler.class),
            mock(ClickHouseConnectionService.class));

    service
        .review("request", new ApprovalTransitionRequest(3, null, "approved"), true, ADMIN)
        .block();

    verify(repository)
        .transition(
            eq("tenant"),
            eq("request"),
            eq(3L),
            eq(ApprovalStatus.SUBMITTED),
            eq(ApprovalStatus.APPROVED),
            eq("admin"),
            eq("admin"),
            eq("approved"),
            any());
  }

  @Test
  void approvalListUsesDatabasePaginationWithoutAResultCap() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    when(repository.countRequests(
            eq("tenant"), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null)))
        .thenReturn(250L);
    when(repository.findRequests(
            eq("tenant"),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(20),
            eq(10)))
        .thenReturn(List.of(detail().request()));
    ApprovalCommandService service =
        service(
            repository,
            mock(DdlWorkOrderTypeCatalog.class),
            mock(DdlPlanCompiler.class),
            mock(ClickHouseConnectionService.class));

    var page = service.list(null, null, null, null, null, null, 3, 10, ADMIN).block();

    assertThat(page).isNotNull();
    assertThat(page.total()).isEqualTo(250);
    assertThat(page.page()).isEqualTo(3);
    assertThat(page.items()).hasSize(1);
    verify(repository)
        .findRequests(
            eq("tenant"),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(null),
            eq(20),
            eq(10));
  }

  @Test
  void applicantCanInterruptOwnSubmittedRequest() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ApprovalDetail submitted = detail(ApprovalStatus.SUBMITTED, "admin", 3);
    when(repository.findDetail("tenant", "request")).thenReturn(Optional.of(submitted));
    when(repository.transition(
            eq("tenant"),
            eq("request"),
            eq(3L),
            eq(ApprovalStatus.SUBMITTED),
            eq(ApprovalStatus.CANCELLED),
            eq(null),
            eq(null),
            eq(null),
            any()))
        .thenReturn(true);
    ApprovalCommandService service =
        service(
            repository,
            mock(DdlWorkOrderTypeCatalog.class),
            mock(DdlPlanCompiler.class),
            mock(ClickHouseConnectionService.class));

    service.interrupt("request", new ApprovalTransitionRequest(3, null, null), ADMIN).block();

    verify(repository)
        .transition(
            eq("tenant"),
            eq("request"),
            eq(3L),
            eq(ApprovalStatus.SUBMITTED),
            eq(ApprovalStatus.CANCELLED),
            eq(null),
            eq(null),
            eq(null),
            any());
  }

  @Test
  void administratorCannotInterruptAnotherApplicantsRequest() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    when(repository.findDetail("tenant", "request"))
        .thenReturn(Optional.of(detail(ApprovalStatus.SUBMITTED, "another-user", 3)));
    ApprovalCommandService service =
        service(
            repository,
            mock(DdlWorkOrderTypeCatalog.class),
            mock(DdlPlanCompiler.class),
            mock(ClickHouseConnectionService.class));

    assertThatThrownBy(
            () ->
                service
                    .interrupt("request", new ApprovalTransitionRequest(3, null, null), ADMIN)
                    .block())
        .hasMessageContaining("ApprovalRequest");

    verify(repository, never())
        .transition(anyString(), anyString(), anyLong(), any(), any(), any(), any(), any(), any());
  }

  @Test
  void manualExecutionRunsFrozenItemsSequentiallyAndFinishesRequest() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    DdlPlanCompiler compiler = mock(DdlPlanCompiler.class);
    ApprovalDetail detail = detail();
    when(repository.findDetail("tenant", "request")).thenReturn(Optional.of(detail));
    when(repository.beginExecution(eq("tenant"), eq("request"), eq(3L), eq("admin"), any()))
        .thenReturn(1);
    when(repository.createExecution(
            anyString(), anyString(), anyString(), eq(1), anyInt(), anyString()))
        .thenReturn("execution-1", "execution-2");
    when(repository.createNodeExecution(
            anyString(), anyString(), anyString(), anyString(), anyInt()))
        .thenReturn("node-1", "node-2");
    when(connections.findById("connection", ADMIN)).thenReturn(Mono.just(connection()));
    when(connections.query(eq("connection"), anyString(), anyMap(), any()))
        .thenReturn(Mono.just("{}"));
    when(compiler.compile(any(), any(), eq(DdlSchemaSnapshot.EMPTY)))
        .thenReturn(
            new CompiledDdlPlan(
                List.of(statement(1, "DDL ONE"), statement(2, "DDL TWO")), List.of()));
    ApprovalCommandService service =
        new ApprovalCommandService(
            repository,
            userAccounts(),
            mock(DdlWorkOrderTypeCatalog.class),
            compiler,
            mock(DdlSchemaInspector.class),
            connections,
            new ObjectMapper(),
            Schedulers.immediate());

    ApprovalDetail result =
        service.execute("request", new ApprovalTransitionRequest(3, null, null), ADMIN).block();

    assertThat(result).isNotNull();
    InOrder order = inOrder(connections);
    order.verify(connections).query(eq("connection"), eq("DDL ONE"), anyMap(), any());
    order.verify(connections).query(eq("connection"), eq("DDL TWO"), anyMap(), any());
    verify(repository, times(2))
        .createNodeExecution(
            eq("tenant"), anyString(), eq("localhost:80"), eq("localhost"), eq(80));
    verify(repository, times(2))
        .finishNodeExecution(eq("tenant"), anyString(), eq(true), anyLong(), eq(null), eq(null));
    verify(repository)
        .finishRequestExecution(
            eq("tenant"),
            eq("request"),
            eq(4L),
            eq(ApprovalStatus.RUNNING),
            eq(ApprovalStatus.SUCCEEDED),
            eq("admin"),
            any());
  }

  @Test
  void executionStopsBeforeClickHouseWhenFrozenPlanNoLongerMatches() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    DdlPlanCompiler compiler = mock(DdlPlanCompiler.class);
    when(repository.findDetail("tenant", "request")).thenReturn(Optional.of(detail()));
    when(compiler.compile(any(), any(), eq(DdlSchemaSnapshot.EMPTY)))
        .thenReturn(new CompiledDdlPlan(List.of(statement(1, "CHANGED DDL")), List.of()));
    ApprovalCommandService service =
        service(repository, mock(DdlWorkOrderTypeCatalog.class), compiler, connections);

    assertThatThrownBy(
            () ->
                service
                    .execute("request", new ApprovalTransitionRequest(3, null, null), ADMIN)
                    .block())
        .hasMessageContaining("schema changed");

    verify(repository, never())
        .beginExecution(anyString(), anyString(), anyLong(), anyString(), any());
    verify(connections, never()).query(anyString(), anyString(), anyMap(), any());
  }

  @Test
  void repeatedAgentPrepareReturnsOriginalDraft() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    DdlWorkOrderTypeCatalog catalog = mock(DdlWorkOrderTypeCatalog.class);
    DdlPlanCompiler compiler = mock(DdlPlanCompiler.class);
    ApprovalTypeDefinition definition = definition();
    when(catalog.requireEnabled("tenant", "CLICKHOUSE_CREATE_TABLE")).thenReturn(definition);
    when(connections.findById("connection", ADMIN)).thenReturn(Mono.just(connection()));
    when(compiler.compile(any(), eq(definition), eq(DdlSchemaSnapshot.EMPTY)))
        .thenReturn(new CompiledDdlPlan(List.of(statement(1, "DDL ONE")), List.of("rule")));
    AtomicReference<ApprovalDetail> saved = new AtomicReference<>();
    AtomicInteger idempotencyLookups = new AtomicInteger();
    when(repository.findDetailByIdempotencyKey(eq("tenant"), eq("admin"), anyString()))
        .thenAnswer(
            invocation ->
                idempotencyLookups.getAndIncrement() == 1
                    ? Optional.ofNullable(saved.get())
                    : Optional.empty());
    org.mockito.Mockito.doAnswer(
            invocation -> {
              ApprovalRequest request = invocation.getArgument(0);
              List<ApprovalItem> items = invocation.getArgument(1);
              saved.set(new ApprovalDetail(request, items, List.of()));
              return null;
            })
        .when(repository)
        .createDraft(any(), any(), anyString(), any());
    ApprovalCommandService service = service(repository, catalog, compiler, connections);
    DdlApprovalPrepareRequest command =
        new DdlApprovalPrepareRequest(
            "connection",
            "CLICKHOUSE_CREATE_TABLE",
            "Create table",
            null,
            new ObjectMapper().createObjectNode(),
            "session",
            "run",
            null,
            null);

    var first = service.prepare(command, ADMIN).block();
    var replay = service.prepare(command, ADMIN).block();

    assertThat(replay.draftId()).isEqualTo(first.draftId());
    assertThat(replay.submittable()).isTrue();
    assertThat(saved.get().request().applicantDisplayName()).isEqualTo("Administrator");
    assertThat(saved.get().request().planHash()).isNotNull();
    assertThat(saved.get().request().planVersion()).isEqualTo(1);
    assertThat(first.planHash()).isEqualTo(saved.get().request().planHash());
    assertThat(replay.planHash()).isEqualTo(first.planHash());
    verify(repository, times(1)).createDraft(any(), any(), anyString(), any());

    when(repository.findDetail("tenant", first.draftId()))
        .thenAnswer(invocation -> Optional.of(saved.get()));
    DdlApprovalPrepareRequest staleUpdate =
        new DdlApprovalPrepareRequest(
            "connection",
            "CLICKHOUSE_CREATE_TABLE",
            "Changed title",
            null,
            new ObjectMapper().createObjectNode(),
            "session",
            "another-run",
            first.draftId(),
            0L);
    assertThatThrownBy(() -> service.prepare(staleUpdate, ADMIN).block())
        .hasMessageContaining("draft changed");
    verify(repository, times(1)).updateDraft(any(), eq(0L), any(), anyString(), any());
  }

  @Test
  void planHashIsStableAndVersionBumpsOnSemanticChange() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    DdlWorkOrderTypeCatalog catalog = mock(DdlWorkOrderTypeCatalog.class);
    DdlPlanCompiler compiler = mock(DdlPlanCompiler.class);
    ApprovalTypeDefinition definition = definition();
    when(catalog.requireEnabled("tenant", "CLICKHOUSE_CREATE_TABLE")).thenReturn(definition);
    when(connections.findById("connection", ADMIN)).thenReturn(Mono.just(connection()));
    when(compiler.compile(any(), eq(definition), eq(DdlSchemaSnapshot.EMPTY)))
        .thenReturn(new CompiledDdlPlan(List.of(statement(1, "DDL ONE")), List.of("rule")));
    AtomicReference<ApprovalRequest> created = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              created.set(invocation.getArgument(0));
              return null;
            })
        .when(repository)
        .createDraft(any(), any(), anyString(), any());
    ApprovalCommandService service = service(repository, catalog, compiler, connections);
    DdlApprovalPrepareRequest command =
        new DdlApprovalPrepareRequest(
            "connection",
            "CLICKHOUSE_CREATE_TABLE",
            "Create table",
            null,
            new ObjectMapper().createObjectNode(),
            "session",
            "run",
            null,
            null);

    var first = service.prepare(command, ADMIN).block();
    String firstHash = first.planHash();

    assertThat(firstHash).isNotNull();
    assertThat(first.planVersion()).isEqualTo(1);

    // Semantic change (different SQL) must produce a new plan_hash and bump plan_version.
    ApprovalDetail currentDetail = new ApprovalDetail(created.get(), List.of(), List.of());
    when(repository.findDetail("tenant", first.draftId())).thenReturn(Optional.of(currentDetail));
    when(compiler.compile(any(), eq(definition), eq(DdlSchemaSnapshot.EMPTY)))
        .thenReturn(new CompiledDdlPlan(List.of(statement(1, "DDL TWO")), List.of("rule")));
    AtomicReference<ApprovalRequest> updated = new AtomicReference<>();
    when(repository.updateDraft(any(), eq(0L), any(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              updated.set(invocation.getArgument(0));
              return true;
            });
    DdlApprovalPrepareRequest change =
        new DdlApprovalPrepareRequest(
            "connection",
            "CLICKHOUSE_CREATE_TABLE",
            "Create table",
            null,
            new ObjectMapper().createObjectNode(),
            "session",
            "run",
            first.draftId(),
            0L);

    service.prepare(change, ADMIN).block();

    assertThat(updated.get().planHash()).isNotEqualTo(firstHash);
    assertThat(updated.get().planVersion()).isEqualTo(2);
  }

  @Test
  void executeBlocksWhenEnvironmentDriftedFromFrozenSnapshot() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    DdlPlanCompiler compiler = mock(DdlPlanCompiler.class);
    DdlSchemaInspector schemaInspector = mock(DdlSchemaInspector.class);
    DdlWorkOrderTypeCatalog catalog = mock(DdlWorkOrderTypeCatalog.class);
    when(catalog.requireEnabled(anyString(), anyString())).thenReturn(definition());
    String content =
        "{\"workOrderTypeKey\":\"CLICKHOUSE_MODIFY_COLUMN\",\"generationRuleChecksum\":\"checksum\","
            + "\"generatorKey\":\"test-generator\",\"generationRule\":{},"
            + "\"intent\":{\"database\":\"db\",\"table\":\"t\"}}";
    String canonical =
        "{\"generationRule\":{},\"generationRuleChecksum\":\"checksum\",\"generatorKey\":"
            + "\"test-generator\",\"intent\":{\"database\":\"db\",\"table\":\"t\"},"
            + "\"workOrderTypeKey\":\"CLICKHOUSE_MODIFY_COLUMN\"}";
    String envSnapshot =
        "{\"connectionId\":\"connection\",\"workOrderTypeKey\":\"CLICKHOUSE_MODIFY_COLUMN\","
            + "\"schema\":{\"columns\":[\"a\",\"b\"],\"protectedColumns\":[]}}";
    Instant now = Instant.now();
    ApprovalItem modifyItem =
        new ApprovalItem(
            "item-1",
            "tenant",
            "request",
            1,
            DdlOperationKind.ALTER_TABLE_MODIFY_COLUMN,
            "ALTER TABLE db.t MODIFY COLUMN a UInt64",
            "digest",
            "[\"db.t\"]",
            "HIGH",
            "[]",
            "PRECONDITION",
            null,
            now);
    ApprovalRequest request =
        new ApprovalRequest(
            "request",
            "tenant",
            "DDL-1",
            "CLICKHOUSE_MODIFY_COLUMN",
            1,
            "checksum",
            "Title",
            null,
            "admin",
            "Administrator",
            null,
            null,
            "connection",
            "Cluster",
            ApprovalStatus.APPROVED,
            content,
            1,
            digest(canonical),
            "MANUAL_TRIGGER",
            0,
            null,
            null,
            null,
            3L,
            now,
            now,
            now,
            null,
            null,
            now,
            1,
            "plan-hash",
            envSnapshot,
            null);
    when(repository.findDetail("tenant", "request"))
        .thenReturn(Optional.of(new ApprovalDetail(request, List.of(modifyItem), List.of())));
    // recompiled frozen plan still matches the frozen item, so revalidate passes
    when(compiler.compile(any(), any(), any()))
        .thenReturn(
            new CompiledDdlPlan(
                List.of(
                    new CompiledDdlStatement(
                        1,
                        DdlOperationKind.ALTER_TABLE_MODIFY_COLUMN,
                        "ALTER TABLE db.t MODIFY COLUMN a UInt64",
                        List.of("db.t"),
                        "HIGH",
                        List.of(),
                        "PRECONDITION")),
                List.of()));
    // by execute time the table drifted: column c was added since prepare
    when(schemaInspector.inspect(eq("connection"), eq("db"), eq("t"), eq(ADMIN)))
        .thenReturn(Mono.just(new DdlSchemaSnapshot(Set.of("a", "b", "c"), Set.of())));
    ApprovalCommandService service =
        new ApprovalCommandService(
            repository,
            userAccounts(),
            catalog,
            compiler,
            schemaInspector,
            connections,
            new ObjectMapper(),
            Schedulers.immediate());

    assertThatThrownBy(
            () ->
                service
                    .execute("request", new ApprovalTransitionRequest(3, null, null), ADMIN)
                    .block())
        .hasMessageContaining("schema changed");
    verify(repository, never())
        .beginExecution(anyString(), anyString(), anyLong(), anyString(), any());
  }

  private static ApprovalCommandService service(
      ApprovalRepository repository,
      DdlWorkOrderTypeCatalog catalog,
      DdlPlanCompiler compiler,
      ClickHouseConnectionService connections) {
    return new ApprovalCommandService(
        repository,
        userAccounts(),
        catalog,
        compiler,
        mock(DdlSchemaInspector.class),
        connections,
        new ObjectMapper(),
        Schedulers.immediate());
  }

  private static UserAccountRepository userAccounts() {
    UserAccountRepository users = mock(UserAccountRepository.class);
    var account = mock(io.github.ccweixiao.datastoria.common.domain.UserAccount.class);
    when(account.username()).thenReturn("Administrator");
    when(users.findByTenantIdAndUserId("tenant", "admin")).thenReturn(Optional.of(account));
    return users;
  }

  private static ApprovalDetail detail() {
    return detail(ApprovalStatus.APPROVED, "applicant", 3);
  }

  private static ApprovalDetail detail(
      ApprovalStatus status, String applicantUserId, long revision) {
    Instant now = Instant.now();
    String content =
        """
        {"workOrderTypeKey":"CLICKHOUSE_CREATE_TABLE","generationRuleChecksum":"checksum",\
        "generatorKey":"test-generator","generationRule":{},"intent":{}}
        """;
    String canonicalContent =
        "{\"generationRule\":{},\"generationRuleChecksum\":\"checksum\","
            + "\"generatorKey\":\"test-generator\",\"intent\":{},"
            + "\"workOrderTypeKey\":\"CLICKHOUSE_CREATE_TABLE\"}";
    ApprovalRequest request =
        new ApprovalRequest(
            "request",
            "tenant",
            "DDL-1",
            "CLICKHOUSE_CREATE_TABLE",
            1,
            "checksum",
            "Title",
            null,
            applicantUserId,
            "Applicant",
            null,
            null,
            "connection",
            "Cluster",
            status,
            content,
            1,
            digest(canonicalContent),
            "MANUAL_TRIGGER",
            0,
            "reviewer",
            "Reviewer",
            null,
            revision,
            now,
            now,
            now,
            null,
            null,
            now,
            1,
            "plan-hash",
            null,
            null);
    ApprovalItem first = item("item-1", 1, "DDL ONE", now);
    ApprovalItem second = item("item-2", 2, "DDL TWO", now);
    return new ApprovalDetail(request, List.of(first, second), List.of());
  }

  private static ApprovalItem item(String id, int ordinal, String sql, Instant now) {
    return new ApprovalItem(
        id,
        "tenant",
        "request",
        ordinal,
        DdlOperationKind.CREATE_TABLE,
        sql,
        "digest",
        "[]",
        "MEDIUM",
        "[]",
        "PRECONDITION",
        null,
        now);
  }

  private static CompiledDdlStatement statement(int ordinal, String sql) {
    return new CompiledDdlStatement(
        ordinal,
        DdlOperationKind.CREATE_TABLE,
        sql,
        List.of(),
        "MEDIUM",
        List.of(),
        "PRECONDITION");
  }

  private static ApprovalTypeDefinition definition() {
    Instant now = Instant.now();
    return new ApprovalTypeDefinition(
        "type",
        "tenant",
        "CLICKHOUSE_CREATE_TABLE",
        "CLICKHOUSE_DDL",
        "{}",
        "{}",
        "test-generator",
        "[]",
        "{}",
        null,
        "{}",
        "ENABLED",
        1,
        "checksum",
        "system",
        "system",
        "system",
        now,
        now,
        now);
  }

  private static ClickHouseConnectionResponse connection() {
    Instant now = Instant.now();
    return new ClickHouseConnectionResponse(
        "connection",
        "Cluster",
        "http://localhost",
        "default",
        "cluster",
        null,
        true,
        "***",
        true,
        1,
        now,
        now);
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }
}
