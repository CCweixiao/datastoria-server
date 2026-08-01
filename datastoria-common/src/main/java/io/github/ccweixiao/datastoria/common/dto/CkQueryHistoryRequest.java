package io.github.ccweixiao.datastoria.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for saving a query history entry. {@code connectionId} and {@code rawSql} are
 * required; {@code connectionName} is an optional display snapshot. {@code executedAt} is set by
 * the service to the current time, never trusted from the client.
 */
public record CkQueryHistoryRequest(
    @NotBlank String connectionId,
    @NotBlank @Size(max = 65535) String rawSql,
    String connectionName) {}
