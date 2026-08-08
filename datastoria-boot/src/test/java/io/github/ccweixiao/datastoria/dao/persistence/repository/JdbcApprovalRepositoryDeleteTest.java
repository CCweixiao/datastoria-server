package io.github.ccweixiao.datastoria.dao.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcApprovalRepositoryDeleteTest {

  @Test
  void deletesTheCompleteApprovalAggregateInDependencyOrder() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForList(contains("FOR UPDATE"), any(Map.class), eq(String.class)))
        .thenReturn(List.of("SUCCEEDED"));
    when(jdbc.update(any(String.class), any(Map.class))).thenReturn(1);
    JdbcApprovalRepository repository = new JdbcApprovalRepository(jdbc);

    assertThat(repository.deleteAggregate("tenant", "request")).isTrue();

    InOrder order = inOrder(jdbc);
    order.verify(jdbc).queryForList(contains("FOR UPDATE"), any(Map.class), eq(String.class));
    order.verify(jdbc).update(contains("ds_approval_node_execution"), any(Map.class));
    order.verify(jdbc).update(contains("ds_approval_execution WHERE"), any(Map.class));
    order.verify(jdbc).update(contains("ds_approval_resource_claim"), any(Map.class));
    order.verify(jdbc).update(contains("ds_approval_event"), any(Map.class));
    order.verify(jdbc).update(contains("ds_approval_item"), any(Map.class));
    order.verify(jdbc).update(contains("ds_approval_request WHERE"), any(Map.class));
  }

  @Test
  void doesNotDeleteAnyRelatedDataWhileTheWorkOrderIsRunning() {
    NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    when(jdbc.queryForList(contains("FOR UPDATE"), any(Map.class), eq(String.class)))
        .thenReturn(List.of("RUNNING"));
    JdbcApprovalRepository repository = new JdbcApprovalRepository(jdbc);

    assertThat(repository.deleteAggregate("tenant", "request")).isFalse();

    verify(jdbc, never()).update(any(String.class), any(Map.class));
  }
}
