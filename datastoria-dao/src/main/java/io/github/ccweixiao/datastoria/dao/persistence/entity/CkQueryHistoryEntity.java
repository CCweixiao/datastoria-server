package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.CkQueryHistory;

/** Database row POJO for {@code ds_ck_query_history} (hard-deleted; no {@code deleted_at}). */
@TableName("ds_ck_query_history")
public class CkQueryHistoryEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String userId;
  private String connectionId;
  private String connectionName;
  private String rawSql;
  private Instant executedAt;
  private Instant createdAt;

  public static CkQueryHistoryEntity fromDomain(CkQueryHistory h) {
    CkQueryHistoryEntity e = new CkQueryHistoryEntity();
    e.id = h.id();
    e.tenantId = h.tenantId();
    e.userId = h.userId();
    e.connectionId = h.connectionId();
    e.connectionName = h.connectionName();
    e.rawSql = h.rawSql();
    e.executedAt = h.executedAt();
    e.createdAt = h.createdAt();
    return e;
  }

  public CkQueryHistory toDomain() {
    return new CkQueryHistory(
        id, tenantId, userId, connectionId, connectionName, rawSql, executedAt, createdAt);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public void setConnectionId(String connectionId) {
    this.connectionId = connectionId;
  }

  public String getConnectionName() {
    return connectionName;
  }

  public void setConnectionName(String connectionName) {
    this.connectionName = connectionName;
  }

  public String getRawSql() {
    return rawSql;
  }

  public void setRawSql(String rawSql) {
    this.rawSql = rawSql;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(Instant executedAt) {
    this.executedAt = executedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
