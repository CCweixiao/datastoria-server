package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.SessionShare;

/**
 * Database row POJO for {@code ds_session_share}. The {@code active_key} generated column is
 * absent.
 */
@TableName("ds_session_share")
public class SessionShareEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String sessionId;
  private String ownerUserId;
  private String tokenHash;
  private Instant expiresAt;
  private Instant revokedAt;
  private Instant createdAt;

  public static SessionShareEntity fromDomain(SessionShare s) {
    SessionShareEntity e = new SessionShareEntity();
    e.id = s.id();
    e.tenantId = s.tenantId();
    e.sessionId = s.sessionId();
    e.ownerUserId = s.ownerUserId();
    e.tokenHash = s.tokenHash();
    e.expiresAt = s.expiresAt();
    e.revokedAt = s.revokedAt();
    e.createdAt = s.createdAt();
    return e;
  }

  public SessionShare toDomain() {
    return new SessionShare(
        id, tenantId, sessionId, ownerUserId, tokenHash, expiresAt, revokedAt, createdAt);
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

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getOwnerUserId() {
    return ownerUserId;
  }

  public void setOwnerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public void setTokenHash(String tokenHash) {
    this.tokenHash = tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
