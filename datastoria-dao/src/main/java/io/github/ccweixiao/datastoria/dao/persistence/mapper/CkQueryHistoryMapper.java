package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.CkQueryHistoryEntity;

/**
 * Mapper for {@code ds_ck_query_history}. {@link #find} applies the first-level filter ({@code
 * connection_id} + {@code user_id}) with optional keyword on {@code raw_sql}, ordered time-desc.
 * {@link #deleteByUserConnectionSql} implements dedup-on-rerun; {@link #prune} enforces the
 * per-(user, connection) cap by deleting everything outside the newest {@code keep} rows.
 */
public interface CkQueryHistoryMapper extends BaseMapper<CkQueryHistoryEntity> {

  int insertHistory(@Param("entity") CkQueryHistoryEntity entity);

  /**
   * Deletes any prior row for the same (tenant, user, connection, raw_sql) so a re-run moves the
   * entry to the top instead of producing a duplicate.
   */
  int deleteByUserConnectionSql(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("connectionId") String connectionId,
      @Param("rawSql") String rawSql);

  /**
   * Filtered read: (tenant, user, connection) equality, optional keyword on {@code raw_sql},
   * ordered by {@code executed_at DESC, id DESC}. A keyword of {@code null}/empty omits the LIKE
   * clause.
   */
  List<CkQueryHistoryEntity> find(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("connectionId") String connectionId,
      @Param("keyword") String keyword,
      @Param("limit") int limit);

  int deleteOwned(
      @Param("id") String id, @Param("tenantId") String tenantId, @Param("userId") String userId);

  int deleteAllByUserConnection(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("connectionId") String connectionId);

  /**
   * Keeps only the newest {@code keep} rows for (tenant, user, connection) by deleting everything
   * whose id is not in the top-{@code keep} window ordered by {@code executed_at DESC, id DESC}.
   * The double-nested subquery works around MySQL's restriction on referencing the target table in
   * a DELETE subquery.
   */
  int prune(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("connectionId") String connectionId,
      @Param("keep") int keep);
}
