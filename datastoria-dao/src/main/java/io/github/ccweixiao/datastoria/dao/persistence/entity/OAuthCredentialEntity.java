package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.OAuthCredential;

/** Database row POJO for {@code ds_oauth_credential}. */
@TableName("ds_oauth_credential")
public class OAuthCredentialEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String userId;
  private String providerKey;
  private String secretId;
  private String tokenType;
  private String scope;
  private Instant expiresAt;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;

  public static OAuthCredentialEntity fromDomain(OAuthCredential c) {
    OAuthCredentialEntity e = new OAuthCredentialEntity();
    e.id = c.id();
    e.tenantId = c.tenantId();
    e.userId = c.userId();
    e.providerKey = c.providerKey();
    e.secretId = c.secretId();
    e.tokenType = c.tokenType();
    e.scope = c.scope();
    e.expiresAt = c.expiresAt();
    e.revision = c.revision();
    e.createdAt = c.createdAt();
    e.updatedAt = c.updatedAt();
    return e;
  }

  public OAuthCredential toDomain() {
    return new OAuthCredential(
        id,
        tenantId,
        userId,
        providerKey,
        secretId,
        tokenType,
        scope,
        expiresAt,
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

  public String getProviderKey() {
    return providerKey;
  }

  public void setProviderKey(String providerKey) {
    this.providerKey = providerKey;
  }

  public String getSecretId() {
    return secretId;
  }

  public void setSecretId(String secretId) {
    this.secretId = secretId;
  }

  public String getTokenType() {
    return tokenType;
  }

  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
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
