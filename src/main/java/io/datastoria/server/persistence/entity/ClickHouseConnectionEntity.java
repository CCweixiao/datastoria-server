package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.ClickHouseConnection;

/**
 * Database row POJO for {@code ds_clickhouse_connection}. {@code cluster} maps to column {@code
 * cluster_name}.
 */
@TableName("ds_clickhouse_connection")
public class ClickHouseConnectionEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String ownerUserId;
  private String name;
  private String url;
  private String username;

  @TableField("cluster_name")
  private String cluster;

  private byte[] passwordCipher;
  private byte[] passwordNonce;
  private String passwordKeyVersion;
  private String passwordMaskedHint;
  private Boolean enabled;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public static ClickHouseConnectionEntity fromDomain(ClickHouseConnection c) {
    ClickHouseConnectionEntity e = new ClickHouseConnectionEntity();
    e.id = c.id();
    e.tenantId = c.tenantId();
    e.ownerUserId = c.ownerUserId();
    e.name = c.name();
    e.url = c.url();
    e.username = c.username();
    e.cluster = c.cluster();
    e.passwordCipher = c.passwordCipher();
    e.passwordNonce = c.passwordNonce();
    e.passwordKeyVersion = c.passwordKeyVersion();
    e.passwordMaskedHint = c.passwordMaskedHint();
    e.enabled = c.enabled();
    e.revision = c.revision();
    e.createdAt = c.createdAt();
    e.updatedAt = c.updatedAt();
    e.deletedAt = c.deletedAt();
    return e;
  }

  public ClickHouseConnection toDomain() {
    return new ClickHouseConnection(
        id,
        tenantId,
        ownerUserId,
        name,
        url,
        username,
        cluster,
        passwordCipher,
        passwordNonce,
        passwordKeyVersion,
        passwordMaskedHint,
        enabled != null && enabled,
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

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public byte[] getPasswordCipher() {
    return passwordCipher;
  }

  public void setPasswordCipher(byte[] passwordCipher) {
    this.passwordCipher = passwordCipher;
  }

  public byte[] getPasswordNonce() {
    return passwordNonce;
  }

  public void setPasswordNonce(byte[] passwordNonce) {
    this.passwordNonce = passwordNonce;
  }

  public String getPasswordKeyVersion() {
    return passwordKeyVersion;
  }

  public void setPasswordKeyVersion(String passwordKeyVersion) {
    this.passwordKeyVersion = passwordKeyVersion;
  }

  public String getPasswordMaskedHint() {
    return passwordMaskedHint;
  }

  public void setPasswordMaskedHint(String passwordMaskedHint) {
    this.passwordMaskedHint = passwordMaskedHint;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
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
