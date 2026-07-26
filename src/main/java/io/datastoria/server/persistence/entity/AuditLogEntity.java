package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.AuditLog;

/** Database row POJO for {@code ds_audit_log}. {@code id} is a DB-generated auto-increment. */
@TableName("ds_audit_log")
public class AuditLogEntity {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  private String tenantId;
  private String actor;
  private String action;
  private String resourceType;
  private String resourceId;
  private String requestId;
  private String safeDiff;
  private String result;
  private Instant createdAt;

  public AuditLog toDomain() {
    return new AuditLog(
        id,
        tenantId,
        actor,
        action,
        resourceType,
        resourceId,
        requestId,
        safeDiff,
        result,
        createdAt);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getActor() {
    return actor;
  }

  public void setActor(String actor) {
    this.actor = actor;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getSafeDiff() {
    return safeDiff;
  }

  public void setSafeDiff(String safeDiff) {
    this.safeDiff = safeDiff;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
