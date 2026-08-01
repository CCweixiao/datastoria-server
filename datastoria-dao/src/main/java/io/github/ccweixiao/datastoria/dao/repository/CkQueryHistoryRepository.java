package io.github.ccweixiao.datastoria.dao.repository;

import java.util.List;
import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.CkQueryHistory;

/**
 * Persistent access to ClickHouse query history. Every method is scoped by {@code tenantId}; reads
 * and writes additionally scope by {@code userId}. {@link #save} dedups on rerun and enforces the
 * per-(user, connection) cap.
 */
public interface CkQueryHistoryRepository {

  /**
   * Saves an entry: removes any prior row with the same raw_sql for this (tenant, user,
   * connection), inserts the new row, then prunes to the newest entries per (user, connection).
   * Returns the saved row.
   */
  CkQueryHistory save(CkQueryHistory entry);

  /**
   * Lists entries for (tenant, user, connection), optionally filtered by keyword on raw_sql,
   * ordered time-desc. {@code keyword} null/blank returns the full connection-scoped set. The
   * result is bounded by the configured per-(user, connection) cap.
   */
  List<CkQueryHistory> find(String tenantId, String userId, String connectionId, String keyword);

  /** Hard-deletes one entry; throws NotFound if the row is missing or belongs to another user. */
  void delete(String id, String tenantId, String userId);

  /** Hard-deletes every entry for (tenant, user, connection). */
  void clear(String tenantId, String userId, String connectionId);

  /** Looks up a single entry by id under (tenant, userId). */
  Optional<CkQueryHistory> findById(String id, String tenantId, String userId);
}
