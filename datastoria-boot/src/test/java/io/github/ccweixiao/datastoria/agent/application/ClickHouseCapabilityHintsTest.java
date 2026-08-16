package io.github.ccweixiao.datastoria.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.ccweixiao.datastoria.common.dto.ClickHouseConnectionMetadataResponse;

/**
 * {@link ClickHouseCapabilityHints} is package-private by design (prompt-assembly detail), so this
 * test lives in the same package and exercises the rendering rules: only missing capabilities
 * produce lines, and every line names the workaround.
 */
class ClickHouseCapabilityHintsTest {

  @Test
  void modernServerProducesNoHints() {
    assertThat(ClickHouseCapabilityHints.render(metadata(true, false))).isEmpty();
  }

  @Test
  void everyMissingCapabilityNamesItsWorkaround() {
    List<String> hints = ClickHouseCapabilityHints.render(metadata(false, true));

    assertThat(hints).hasSize(6);
    assertThat(hints.get(0)).contains("query_log").contains("FQDN()");
    assertThat(hints.get(1)).contains("formatQuery()").contains("do not use it");
    assertThat(hints.get(2)).contains("system.functions").contains("description");
    assertThat(hints.get(3)).contains("opentelemetry_span_log");
    assertThat(hints.get(4)).contains("part_log");
    assertThat(hints.get(5)).contains("skip_unavailable_shards").contains("read-only");
  }

  @Test
  void partiallyCapableServerListsOnlyTheGaps() {
    List<String> hints =
        ClickHouseCapabilityHints.render(
            new ClickHouseConnectionMetadataResponse(
                "node",
                "node",
                "22.8.1",
                "readonly",
                "UTC",
                false, // functions.description missing
                true,
                true,
                true, // query_log.hostname present
                true,
                true,
                true, // formatQuery present
                false,
                List.of(),
                null,
                List.of(),
                List.of()));

    assertThat(hints).singleElement().satisfies(hint -> hint.contains("system.functions"));
  }

  private static ClickHouseConnectionMetadataResponse metadata(
      boolean fullyCapable, boolean readonlySetting) {
    return new ClickHouseConnectionMetadataResponse(
        "node",
        "node",
        "24.8.1.1",
        "readonly",
        "UTC",
        fullyCapable,
        fullyCapable,
        fullyCapable,
        fullyCapable,
        fullyCapable,
        fullyCapable,
        fullyCapable,
        readonlySetting,
        List.of(),
        null,
        List.of(),
        List.of());
  }
}
