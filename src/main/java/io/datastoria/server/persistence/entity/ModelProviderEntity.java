package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.ModelProvider;

/**
 * Database row POJO for {@code ds_model_provider}. The {@code active_key} generated column is
 * absent.
 */
@TableName("ds_model_provider")
public class ModelProviderEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String providerKey;
  private String displayName;
  private String baseUrl;
  private String authType;
  private Boolean enabled;
  private String configJson;
  private String secretId;
  private Long revision;
  private String createdBy;
  private String updatedBy;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public static ModelProviderEntity fromDomain(ModelProvider p) {
    ModelProviderEntity e = new ModelProviderEntity();
    e.id = p.id();
    e.tenantId = p.tenantId();
    e.providerKey = p.providerKey();
    e.displayName = p.displayName();
    e.baseUrl = p.baseUrl();
    e.authType = p.authType();
    e.enabled = p.enabled();
    e.configJson = p.configJson();
    e.secretId = p.secretId();
    e.revision = p.revision();
    e.createdBy = p.createdBy();
    e.updatedBy = p.updatedBy();
    e.createdAt = p.createdAt();
    e.updatedAt = p.updatedAt();
    e.deletedAt = p.deletedAt();
    return e;
  }

  public ModelProvider toDomain() {
    return new ModelProvider(
        id,
        tenantId,
        providerKey,
        displayName,
        baseUrl,
        authType,
        enabled != null && enabled,
        configJson,
        secretId,
        revision != null ? revision : 0L,
        createdBy,
        updatedBy,
        createdAt,
        updatedAt,
        deletedAt);
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

  public String getProviderKey() {
    return providerKey;
  }

  public void setProviderKey(String providerKey) {
    this.providerKey = providerKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getAuthType() {
    return authType;
  }

  public void setAuthType(String authType) {
    this.authType = authType;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public String getConfigJson() {
    return configJson;
  }

  public void setConfigJson(String configJson) {
    this.configJson = configJson;
  }

  public String getSecretId() {
    return secretId;
  }

  public void setSecretId(String secretId) {
    this.secretId = secretId;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
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

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
