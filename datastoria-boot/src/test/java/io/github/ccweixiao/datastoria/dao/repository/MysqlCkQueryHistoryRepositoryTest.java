package io.github.ccweixiao.datastoria.dao.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import io.github.ccweixiao.datastoria.boot.TestDbHelper;
import io.github.ccweixiao.datastoria.common.domain.CkQueryHistory;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;

/**
 * MySQL-backed tests for {@link CkQueryHistoryRepository}. Covers the first-level filter
 * (connection_id + user_id), time-desc ordering, keyword LIKE on raw_sql, dedup-on-rerun, the
 * per-(user, connection) cap (the prune SQL), and hard delete/clear.
 */
@SpringBootTest
@ActiveProfiles("dev")
class MysqlCkQueryHistoryRepositoryTest {

  private static final String TENANT = "tenant-test";
  private static final String USER = "dev@example.com";
  private static final String CONN = "ch-test";

  @Autowired CkQueryHistoryRepository repo;
  @Autowired TestDbHelper dbHelper;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void clean() {
    dbHelper.cleanAll();
  }

  @Test
  void saveAndFindByIdRoundTrip() {
    CkQueryHistory saved = repo.save(entry("SELECT 1"));
    assertThat(saved.id()).isNotBlank();
    assertThat(saved.executedAt()).isNotNull();
    assertThat(saved.createdAt()).isNotNull();

    CkQueryHistory found = repo.findById(saved.id(), TENANT, USER).orElseThrow();
    assertThat(found.rawSql()).isEqualTo("SELECT 1");
    assertThat(found.connectionId()).isEqualTo(CONN);
    assertThat(found.connectionName()).isEqualTo(CONN);
  }

  @Test
  void findByIdExcludesOtherUser() {
    insert(
        "h_other",
        CONN,
        "SELECT 1",
        TENANT,
        "other@example.com",
        Instant.parse("2026-08-01T00:00:00Z"));
    assertThat(repo.findById("h_other", TENANT, USER)).isEmpty();
    assertThat(repo.findById("h_other", TENANT, "other@example.com")).isPresent();
  }

  @Test
  void findOrdersByExecutedAtDesc() {
    insert("h1", CONN, "SELECT 1", "2026-08-01T00:00:01Z");
    insert("h2", CONN, "SELECT 2", "2026-08-01T00:00:03Z");
    insert("h3", CONN, "SELECT 3", "2026-08-01T00:00:02Z");

    var rows = repo.find(TENANT, USER, CONN, null);
    assertThat(rows).extracting(CkQueryHistory::id).containsExactly("h2", "h3", "h1");
  }

  @Test
  void findFiltersByConnectionId() {
    insert("h_a", "ch-prod", "SELECT 1", "2026-08-01T00:00:01Z");
    insert("h_b", CONN, "SELECT 2", "2026-08-01T00:00:02Z");
    insert("h_c", CONN, "SELECT 3", "2026-08-01T00:00:03Z");

    var rows = repo.find(TENANT, USER, CONN, null);
    assertThat(rows).hasSize(2);
    assertThat(rows).allSatisfy(h -> assertThat(h.connectionId()).isEqualTo(CONN));
  }

  @Test
  void findKeywordFiltersRawSqlCaseInsensitively() {
    insert("h1", CONN, "SELECT * FROM orders", "2026-08-01T00:00:01Z");
    insert("h2", CONN, "SELECT count() FROM events", "2026-08-01T00:00:02Z");
    insert("h3", CONN, "SHOW TABLES", "2026-08-01T00:00:03Z");

    var rows = repo.find(TENANT, USER, CONN, "orders");
    assertThat(rows).extracting(CkQueryHistory::id).containsExactly("h1");

    var selectRows = repo.find(TENANT, USER, CONN, "select");
    assertThat(selectRows).extracting(CkQueryHistory::id).containsExactly("h2", "h1");
  }

  @Test
  void saveDedupsOnRerunKeepingNewestRow() {
    CkQueryHistory first = repo.save(entry("SELECT 1"));
    CkQueryHistory second = repo.save(entry("SELECT 1"));

    assertThat(repo.find(TENANT, USER, CONN, null)).hasSize(1);
    assertThat(repo.findById(first.id(), TENANT, USER)).isEmpty();
    assertThat(repo.findById(second.id(), TENANT, USER)).isPresent();
  }

  @Test
  void saveDedupsIsScopedPerConnection() {
    repo.save(entry("SELECT 1", CONN));
    repo.save(entry("SELECT 1", "ch-other"));

    assertThat(repo.find(TENANT, USER, CONN, null)).hasSize(1);
    assertThat(repo.find(TENANT, USER, "ch-other", null)).hasSize(1);
  }

  @Test
  void savePrunesToCapKeepingNewest() {
    // 105 fixture rows with deterministic, increasing executed_at (oldest = SELECT 0).
    Instant base = Instant.parse("2026-08-01T00:00:00Z");
    for (int i = 0; i < 105; i++) {
      insert("h" + i, CONN, "SELECT " + i, base.plusSeconds(i));
    }
    // A fresh save (executed_at = now, the newest) triggers prune to the configured cap of 100.
    repo.save(entry("SELECT NEW"));

    var rows = repo.find(TENANT, USER, CONN, null);
    assertThat(rows).hasSize(100);
    // The 6 oldest fixtures (SELECT 0..5) are evicted; SELECT 6 and the new entry survive.
    assertThat(rows).extracting(CkQueryHistory::rawSql).doesNotContain("SELECT 0", "SELECT 5");
    assertThat(rows).extracting(CkQueryHistory::rawSql).contains("SELECT 6", "SELECT NEW");
  }

  @Test
  void deleteRemovesRow() {
    CkQueryHistory saved = repo.save(entry("SELECT 1"));
    repo.delete(saved.id(), TENANT, USER);
    assertThat(repo.findById(saved.id(), TENANT, USER)).isEmpty();
  }

  @Test
  void deleteThrowsNotFoundForMissingOrCrossUser() {
    assertThatThrownBy(() -> repo.delete("nope", TENANT, USER))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void clearRemovesAllForConnection() {
    repo.save(entry("SELECT 1", CONN));
    repo.save(entry("SELECT 2", CONN));
    repo.save(entry("SELECT 3", "ch-other"));

    repo.clear(TENANT, USER, CONN);
    assertThat(repo.find(TENANT, USER, CONN, null)).isEmpty();
    assertThat(repo.find(TENANT, USER, "ch-other", null)).hasSize(1);
  }

  private CkQueryHistory entry(String sql) {
    return entry(sql, CONN);
  }

  private CkQueryHistory entry(String sql, String conn) {
    return new CkQueryHistory(Ulid.next(), TENANT, USER, conn, conn, sql, null, null);
  }

  private void insert(String id, String conn, String sql, String executedAt) {
    insert(id, conn, sql, TENANT, USER, Instant.parse(executedAt));
  }

  private void insert(String id, String conn, String sql, Instant executedAt) {
    insert(id, conn, sql, TENANT, USER, executedAt);
  }

  private void insert(
      String id, String conn, String sql, String tenant, String user, Instant executedAt) {
    Timestamp ts = Timestamp.from(executedAt);
    jdbc.sql(
            "INSERT INTO ds_ck_query_history (id, tenant_id, user_id, connection_id, "
                + "connection_name, raw_sql, executed_at, created_at) "
                + "VALUES (:id, :tenant, :user, :conn, :connName, :sql, :ts, :ts)")
        .param("id", id)
        .param("tenant", tenant)
        .param("user", user)
        .param("conn", conn)
        .param("connName", conn)
        .param("sql", sql)
        .param("ts", ts)
        .update();
  }
}
