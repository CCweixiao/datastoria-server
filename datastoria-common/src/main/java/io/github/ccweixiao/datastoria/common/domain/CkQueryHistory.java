package io.github.ccweixiao.datastoria.common.domain;

import java.time.Instant;

/**
 * A saved ClickHouse SQL query, owned by a single ({@code tenantId}, {@code userId}) and bound to a
 * cluster connection. {@code executedAt} is the query run time used for time-desc ordering; {@code
 * connectionName} is a denormalized display snapshot captured at save time.
 */
public record CkQueryHistory(
    String id,
    String tenantId,
    String userId,
    String connectionId,
    String connectionName,
    String rawSql,
    Instant executedAt,
    Instant createdAt) {}
