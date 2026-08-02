package io.github.ccweixiao.datastoria.service.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalDetail;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalItem;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalRequest;
import io.github.ccweixiao.datastoria.common.domain.approval.ApprovalStatus;
import io.github.ccweixiao.datastoria.common.domain.approval.DdlOperationKind;
import io.github.ccweixiao.datastoria.common.dto.approval.ApprovalTransitionRequest;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ApprovalRepository;
import io.github.ccweixiao.datastoria.service.ClickHouseConnectionService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class ApprovalCommandServiceTest {

  @Test
  void manualExecutionRunsFrozenItemsSequentiallyAndFinishesRequest() {
    ApprovalRepository repository = mock(ApprovalRepository.class);
    ClickHouseConnectionService connections = mock(ClickHouseConnectionService.class);
    ApprovalDetail detail = detail();
    when(repository.findDetail("tenant", "request")).thenReturn(Optional.of(detail));
    when(repository.beginExecution(eq("tenant"), eq("request"), eq(3L), eq("admin"), any()))
        .thenReturn(1);
    when(repository.createExecution(
            anyString(), anyString(), anyString(), eq(1), anyInt(), anyString()))
        .thenReturn("execution-1", "execution-2");
    when(connections.query(eq("connection"), anyString(), anyMap(), any()))
        .thenReturn(Mono.just("{}"));
    ApprovalCommandService service =
        new ApprovalCommandService(
            repository,
            mock(DdlWorkOrderTypeCatalog.class),
            mock(DdlPlanCompiler.class),
            mock(DdlSchemaInspector.class),
            connections,
            new ObjectMapper(),
            Schedulers.immediate());

    ApprovalDetail result =
        service
            .execute(
                "request",
                new ApprovalTransitionRequest(3, null, null),
                new Identity("tenant", "admin", Set.of("ROLE_ADMIN")))
            .block();

    assertThat(result).isNotNull();
    InOrder order = inOrder(connections);
    order.verify(connections).query(eq("connection"), eq("DDL ONE"), anyMap(), any());
    order.verify(connections).query(eq("connection"), eq("DDL TWO"), anyMap(), any());
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

  private static ApprovalDetail detail() {
    Instant now = Instant.now();
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
            "{}",
            1,
            "digest",
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
}
