package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.ccweixiao.datastoria.common.config.ClickHouseQuerySecurityProperties;

class ClickHouseConnectionServiceTest {

  @Test
  void wrapsNodeQueryWithServerSideCredentials() {
    String wrapped =
        ClickHouseConnectionService.wrapForTargetNode(
            "SELECT 1", "node-1.example", "internal'user", "configured", "pa'ss\\word");

    assertThat(wrapped)
        .contains("'node-1.example'")
        .contains("SELECT 1")
        .contains("'internal\\'user'")
        .contains("'pa\\'ss\\\\word'");
  }

  @Test
  void leavesOrdinaryQueryUnchanged() {
    assertThat(
            ClickHouseConnectionService.wrapForTargetNode(
                "SELECT 1", null, null, "default", "secret"))
        .isEqualTo("SELECT 1");
  }

  @Test
  void rejectsUnsafeTargetNode() {
    assertThatThrownBy(
            () ->
                ClickHouseConnectionService.wrapForTargetNode(
                    "SELECT 1", "node'); DROP TABLE x; --", null, "default", "secret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid ClickHouse target node");
  }

  @Test
  void wrapsUnbracketedIpv6TargetNodeBeforeUsingRemote() {
    String wrapped =
        ClickHouseConnectionService.wrapForTargetNode(
            "SELECT 1", "::1:9000", null, "default", "secret");

    assertThat(wrapped).contains("'[::1]:9000'");
  }

  @Test
  void keepsBracketedIpv6TargetNode() {
    assertThat(ClickHouseConnectionService.normalizeTargetNode("[2001:db8::10]:9440"))
        .isEqualTo("[2001:db8::10]:9440");
  }

  @Test
  void readOnlyLimitsCannotBeRelaxedByQueryParameters() {
    Map<String, Object> safe =
        ClickHouseConnectionService.enforceReadOnlyLimits(
            Map.of(
                "readonly", 0,
                "allow_ddl", 1,
                "max_execution_time", 3600,
                "max_rows_to_read", Long.MAX_VALUE,
                "max_result_rows", 100,
                "param_customer_id", 42),
            querySecurity());

    assertThat(safe)
        .containsEntry("readonly", 2)
        .containsEntry("allow_ddl", 0)
        .containsEntry("max_execution_time", 30L)
        .containsEntry("max_rows_to_read", 10_000_000L)
        .containsEntry("max_result_rows", 100L)
        .containsEntry("param_customer_id", 42);
  }

  static ClickHouseQuerySecurityProperties querySecurity() {
    ClickHouseQuerySecurityProperties properties = new ClickHouseQuerySecurityProperties();
    properties.setReadOnly(true);
    properties.setAllowDdl(false);
    properties.setAllowIntrospectionFunctions(false);
    properties.setMaxExecutionTime(30);
    properties.setMaxResultRows(10_000);
    properties.setMaxResultBytes(10_000_000);
    properties.setMaxRowsToRead(10_000_000);
    properties.setMaxBytesToRead(1_000_000_000);
    properties.setMaxMemoryUsage(1_000_000_000);
    properties.setMaxThreads(4);
    properties.setResultOverflowMode("break");
    properties.setReadOverflowMode("throw");
    return properties;
  }
}
