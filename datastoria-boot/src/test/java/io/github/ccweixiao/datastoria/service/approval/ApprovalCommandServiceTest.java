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
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ApprovalCommandServiceTest {

  private static final Identity ADMIN = new Identity("tenant", "admin", Set.of("ROLE_ADMIN"));

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
    when(repository.findDetailByIdempotencyKey(eq("tenant"), eq("admin"), anyString()))
        .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
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
            "run");

    var first = service.prepare(command, ADMIN).block();
    var replay = service.prepare(command, ADMIN).block();

    assertThat(replay.draftId()).isEqualTo(first.draftId());
    assertThat(replay.submittable()).isTrue();
    verify(repository, times(1)).createDraft(any(), any(), anyString(), any());
  }

  private static ApprovalCommandService service(
      ApprovalRepository repository,
      DdlWorkOrderTypeCatalog catalog,
      DdlPlanCompiler compiler,
      ClickHouseConnectionService connections) {
    return new ApprovalCommandService(
        repository,
        catalog,
        compiler,
        mock(DdlSchemaInspector.class),
        connections,
        new ObjectMapper(),
        Schedulers.immediate());
  }

  private static ApprovalDetail detail() {
    Instant now = Instant.now();
    String content =
        """
        {"workOrderTypeKey":"CLICKHOUSE_CREATE_TABLE","generationRuleChecksum":"checksum",\
        "generatorKey":"test-generator","generationRule":{},"intent":{}}
        """;
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
            "applicant",
            "Applicant",
            null,
            null,
            "connection",
            "Cluster",
            ApprovalStatus.APPROVED,
            content,
            1,
            digest(content),
            "MANUAL_TRIGGER",
            0,
            "reviewer",
            "Reviewer",
            null,
            3,
            now,
            now,
            now,
            null,
            null,
            now);
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
