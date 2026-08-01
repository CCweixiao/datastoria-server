package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.Model;

/**
 * Database row POJO for {@code ds_model}. Not an immutable domain record: MyBatis-Plus mutates
 * fields by setter. The {@code active_key} generated column is intentionally absent — it is
 * GENERATED ALWAYS AS, so it must never appear in INSERT/UPDATE column lists.
 */
@TableName("ds_model")
public class ModelEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String ownerUserId;
  private String providerId;
  private String modelKey;
  private String displayName;
  private String description;
  private String source;
  private Boolean enabled;
  private Boolean isFree;
  private String capabilitiesJson;
  private String generationDefaultsJson;
  private String secretId;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public static ModelEntity fromDomain(Model m) {
    ModelEntity e = new ModelEntity();
    e.id = m.id();
    e.tenantId = m.tenantId();
    e.ownerUserId = m.ownerUserId();
    e.providerId = m.providerId();
    e.modelKey = m.modelKey();
    e.displayName = m.displayName();
    e.description = m.description();
    e.source = m.source();
    e.enabled = m.enabled();
    e.isFree = m.isFree();
    e.capabilitiesJson = m.capabilitiesJson();
    e.generationDefaultsJson = m.generationDefaultsJson();
    e.secretId = m.secretId();
    e.revision = m.revision();
    e.createdAt = m.createdAt();
    e.updatedAt = m.updatedAt();
    e.deletedAt = m.deletedAt();
    return e;
  }

  public Model toDomain() {
    return new Model(
        id,
        tenantId,
        ownerUserId,
        providerId,
        modelKey,
        displayName,
        description,
        source,
        enabled != null && enabled,
        isFree != null && isFree,
        capabilitiesJson,
        generationDefaultsJson,
        secretId,
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

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public String getProviderId() {
    return providerId;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public String getModelKey() {
    return modelKey;
  }

  public void setModelKey(String modelKey) {
    this.modelKey = modelKey;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Boolean getIsFree() {
    return isFree;
  }

  public void setIsFree(Boolean isFree) {
    this.isFree = isFree;
  }

  public String getCapabilitiesJson() {
    return capabilitiesJson;
  }

  public void setCapabilitiesJson(String capabilitiesJson) {
    this.capabilitiesJson = capabilitiesJson;
  }

  public String getGenerationDefaultsJson() {
    return generationDefaultsJson;
  }

  public void setGenerationDefaultsJson(String generationDefaultsJson) {
    this.generationDefaultsJson = generationDefaultsJson;
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
