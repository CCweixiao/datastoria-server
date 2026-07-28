package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

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
}
