package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.ConfigEntry;

/** Database row POJO for {@code ds_config_entry}. */
@TableName("ds_config_entry")
public class ConfigEntryEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String scopeType;
  private String scopeId;
  private String configKey;
  private String valueJson;
  private String schemaVersion;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public static ConfigEntryEntity fromDomain(ConfigEntry c) {
    ConfigEntryEntity e = new ConfigEntryEntity();
    e.id = c.id();
    e.tenantId = c.tenantId();
    e.scopeType = c.scopeType();
    e.scopeId = c.scopeId();
    e.configKey = c.configKey();
    e.valueJson = c.valueJson();
    e.schemaVersion = c.schemaVersion();
    e.revision = c.revision();
    e.createdAt = c.createdAt();
    e.updatedAt = c.updatedAt();
    e.deletedAt = c.deletedAt();
    return e;
  }

  public ConfigEntry toDomain() {
    return new ConfigEntry(
        id,
        tenantId,
        scopeType,
        scopeId,
        configKey,
        valueJson,
        schemaVersion,
        revision != null ? revision : 0L,
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

  public String getScopeType() {
    return scopeType;
  }

  public void setScopeType(String scopeType) {
    this.scopeType = scopeType;
  }

  public String getScopeId() {
    return scopeId;
  }

  public void setScopeId(String scopeId) {
    this.scopeId = scopeId;
  }

  public String getConfigKey() {
    return configKey;
  }

  public void setConfigKey(String configKey) {
    this.configKey = configKey;
  }

  public String getValueJson() {
    return valueJson;
  }

  public void setValueJson(String valueJson) {
    this.valueJson = valueJson;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
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

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}
