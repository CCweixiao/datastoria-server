package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.Secret;

/**
 * Database row POJO for {@code ds_secret}. {@code cipherText}/{@code nonce} are BLOBs (byte[]); the
 * masked read deliberately omits them so they stay null.
 */
@TableName("ds_secret")
public class SecretEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String ownerUserId;
  private String secretKind;
  private byte[] cipherText;
  private String keyVersion;
  private byte[] nonce;
  private String maskedHint;
  private Instant expiresAt;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public static SecretEntity fromDomain(Secret s) {
    SecretEntity e = new SecretEntity();
    e.id = s.id();
    e.tenantId = s.tenantId();
    e.ownerUserId = s.ownerUserId();
    e.secretKind = s.secretKind();
    e.cipherText = s.cipherText();
    e.keyVersion = s.keyVersion();
    e.nonce = s.nonce();
    e.maskedHint = s.maskedHint();
    e.expiresAt = s.expiresAt();
    e.createdAt = s.createdAt();
    e.updatedAt = s.updatedAt();
    e.deletedAt = s.deletedAt();
    return e;
  }

  public Secret toDomain() {
    return new Secret(
        id,
        tenantId,
        ownerUserId,
        secretKind,
        cipherText,
        keyVersion,
        nonce,
        maskedHint,
        expiresAt,
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

  public String getSecretKind() {
    return secretKind;
  }

  public void setSecretKind(String secretKind) {
    this.secretKind = secretKind;
  }

  public byte[] getCipherText() {
    return cipherText;
  }

  public void setCipherText(byte[] cipherText) {
    this.cipherText = cipherText;
  }

  public String getKeyVersion() {
    return keyVersion;
  }

  public void setKeyVersion(String keyVersion) {
    this.keyVersion = keyVersion;
  }

  public byte[] getNonce() {
    return nonce;
  }

  public void setNonce(byte[] nonce) {
    this.nonce = nonce;
  }

  public String getMaskedHint() {
    return maskedHint;
  }

  public void setMaskedHint(String maskedHint) {
    this.maskedHint = maskedHint;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
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
