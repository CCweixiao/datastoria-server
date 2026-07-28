package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.UserModelPreference;

/** Database row POJO for {@code ds_user_model_preference}. */
@TableName("ds_user_model_preference")
public class UserModelPreferenceEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String userId;
  private String selectedModelId;
  private String preferenceJson;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;

  public UserModelPreference toDomain() {
    return new UserModelPreference(
        id,
        tenantId,
        userId,
        selectedModelId,
        preferenceJson,
        revision != null ? revision : 0L,
        createdAt,
        updatedAt);
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

  public String getSelectedModelId() {
    return selectedModelId;
  }

  public void setSelectedModelId(String selectedModelId) {
    this.selectedModelId = selectedModelId;
  }

  public String getPreferenceJson() {
    return preferenceJson;
  }

  public void setPreferenceJson(String preferenceJson) {
    this.preferenceJson = preferenceJson;
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
