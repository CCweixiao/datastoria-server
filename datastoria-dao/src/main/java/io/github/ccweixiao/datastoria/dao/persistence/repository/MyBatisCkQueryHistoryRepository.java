package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.ccweixiao.datastoria.common.domain.CkQueryHistory;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.CkQueryHistoryEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.CkQueryHistoryMapper;
import io.github.ccweixiao.datastoria.dao.repository.CkQueryHistoryRepository;

/**
 * MyBatis-Plus adapter for {@code ds_ck_query_history}. {@link #save} performs dedup-on-rerun
 * (delete the prior same-sql row, insert, then prune to the configured per-(user, connection) cap).
 * The cap defaults to 100, matching the previous client-side history limit.
 */
@Repository
public class MyBatisCkQueryHistoryRepository implements CkQueryHistoryRepository {

  private final CkQueryHistoryMapper mapper;
  private final int maxHistorySize;

  public MyBatisCkQueryHistoryRepository(
      CkQueryHistoryMapper mapper,
      @Value("${datastoria.query-history.max-size:100}") int maxHistorySize) {
    this.mapper = mapper;
    this.maxHistorySize = maxHistorySize;
  }

  @Override
  public CkQueryHistory save(CkQueryHistory entry) {
    mapper.deleteByUserConnectionSql(
        entry.tenantId(), entry.userId(), entry.connectionId(), entry.rawSql());
    mapper.insertHistory(CkQueryHistoryEntity.fromDomain(entry));
    mapper.prune(entry.tenantId(), entry.userId(), entry.connectionId(), maxHistorySize);
    return findById(entry.id(), entry.tenantId(), entry.userId())
        .orElseThrow(() -> new NotFoundException("CkQueryHistory", entry.id()));
  }

  @Override
  public List<CkQueryHistory> find(
      String tenantId, String userId, String connectionId, String keyword) {
    return mapper.find(tenantId, userId, connectionId, keyword, maxHistorySize).stream()
        .map(CkQueryHistoryEntity::toDomain)
        .toList();
  }

  @Override
  public void delete(String id, String tenantId, String userId) {
    int affected = mapper.deleteOwned(id, tenantId, userId);
    if (affected == 0) {
      throw new NotFoundException("CkQueryHistory", id);
    }
  }

  @Override
  public void clear(String tenantId, String userId, String connectionId) {
    mapper.deleteAllByUserConnection(tenantId, userId, connectionId);
  }

  @Override
  public Optional<CkQueryHistory> findById(String id, String tenantId, String userId) {
    CkQueryHistoryEntity e =
        mapper.selectOne(
            new LambdaQueryWrapper<CkQueryHistoryEntity>()
                .eq(CkQueryHistoryEntity::getId, id)
                .eq(CkQueryHistoryEntity::getTenantId, tenantId)
                .eq(CkQueryHistoryEntity::getUserId, userId));
    return Optional.ofNullable(e).map(CkQueryHistoryEntity::toDomain);
  }
}
