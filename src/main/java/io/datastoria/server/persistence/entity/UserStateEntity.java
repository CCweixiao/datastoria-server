package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.UserState;

/**
 * Database row POJO for {@code ds_user_state} (composite key {@code tenant_id, user_id, namespace,
 * state_key}).
 */
@TableName("ds_user_state")
public class UserStateEntity {

  private String tenantId;
  private String userId;
  private String namespace;
  private String stateKey;
  private String valueJson;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;

  public static UserStateEntity fromDomain(UserState s) {
    UserStateEntity e = new UserStateEntity();
    e.tenantId = s.tenantId();
    e.userId = s.userId();
    e.namespace = s.namespace();
    e.stateKey = s.key();
    e.valueJson = s.valueJson();
    e.revision = s.revision();
    e.createdAt = s.createdAt();
    e.updatedAt = s.updatedAt();
    return e;
  }

  public UserState toDomain() {
    return new UserState(
        tenantId,
        userId,
        namespace,
        stateKey,
        valueJson,
        revision != null ? revision : 0L,
        createdAt,
        updatedAt);
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

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getStateKey() {
    return stateKey;
  }

  public void setStateKey(String stateKey) {
    this.stateKey = stateKey;
  }

  public String getValueJson() {
    return valueJson;
  }

  public void setValueJson(String valueJson) {
    this.valueJson = valueJson;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
