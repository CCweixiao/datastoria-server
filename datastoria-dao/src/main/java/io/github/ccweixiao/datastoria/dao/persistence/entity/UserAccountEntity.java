package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.UserAccount;

/** Database row POJO for {@code ds_user_account}. */
@TableName("ds_user_account")
public class UserAccountEntity {

  @TableId(value = "user_id", type = IdType.INPUT)
  private String userId;

  private String tenantId;
  private String username;
  private String email;
  private String passwordHash;
  private String role;
  private Integer status;
  private Instant createdAt;
  private Instant updatedAt;

  public static UserAccountEntity fromDomain(UserAccount a) {
    UserAccountEntity e = new UserAccountEntity();
    e.userId = a.userId();
    e.tenantId = a.tenantId();
    e.username = a.username();
    e.email = a.email();
    e.passwordHash = a.passwordHash();
    e.role = a.role();
    e.status = a.status();
    e.createdAt = a.createdAt();
    e.updatedAt = a.updatedAt();
    return e;
  }

  public UserAccount toDomain() {
    return new UserAccount(
        userId,
        tenantId,
        username,
        email,
        passwordHash,
        role != null ? role : UserAccount.ROLE_USER,
        status != null ? status : 1,
        createdAt,
        updatedAt);
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
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
