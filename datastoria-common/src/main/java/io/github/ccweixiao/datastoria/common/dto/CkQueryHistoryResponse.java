package io.github.ccweixiao.datastoria.common.dto;

import java.time.Instant;

import io.github.ccweixiao.datastoria.common.domain.CkQueryHistory;

/**
 * Wire shape for a query history entry. {@code executedAt} is the query run time (ISO-8601 instant)
 * used for time-desc ordering and display.
 */
public record CkQueryHistoryResponse(
    String id, String connectionId, String connectionName, String rawSql, Instant executedAt) {

  public static CkQueryHistoryResponse from(CkQueryHistory h) {
    return new CkQueryHistoryResponse(
        h.id(), h.connectionId(), h.connectionName(), h.rawSql(), h.executedAt());
  }
}
