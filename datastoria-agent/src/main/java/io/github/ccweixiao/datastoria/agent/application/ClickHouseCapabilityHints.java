package io.github.ccweixiao.datastoria.agent.application;

import java.util.ArrayList;
import java.util.List;

import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionMetadataResponse;

/**
 * Renders the connection's probed capability gaps as short, behavior-changing advice for the
 * agent's system prompt. Only missing capabilities are listed — present ones are the default the
 * model already assumes — and every line names the workaround so the model adapts on the first
 * query instead of failing once and retrying.
 */
final class ClickHouseCapabilityHints {

  private ClickHouseCapabilityHints() {}

  static List<String> render(ClickHouseConnectionMetadataResponse metadata) {
    List<String> hints = new ArrayList<>();
    if (!metadata.queryLogTableHasHostnameColumn()) {
      hints.add(
          "system.query_log has no hostname column; filter per node with FQDN() instead of"
              + " hostname");
    }
    if (!metadata.hasFormatQueryFunction()) {
      hints.add("formatQuery() is unavailable on this server; do not use it to normalize SQL");
    }
    if (!metadata.functionTableHasDescriptionColumn()) {
      hints.add("system.functions has no description column; do not select it");
    }
    if (!metadata.spanLogTableHasHostnameColumn()) {
      hints.add("system.opentelemetry_span_log has no hostname column");
    }
    if (!metadata.partLogTableHasNodeNameColumn()) {
      hints.add("system.part_log has no hostname column");
    }
    if (metadata.readonlySkipUnavailableShards()) {
      hints.add(
          "the skip_unavailable_shards setting is locked read-only; per-query SETTINGS overrides"
              + " for it are rejected");
    }
    return List.copyOf(hints);
  }
}
