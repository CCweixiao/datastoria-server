package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.agent.domain.AgentPendingAction;
import io.datastoria.server.agent.domain.PendingActionStatus;
import io.datastoria.server.agent.domain.PendingActionType;

/**
 * Database row POJO for {@code ds_agent_pending_action}. {@code status}/{@code actionType} are
 * lowercase enum names.
 */
@TableName("ds_agent_pending_action")
public class AgentPendingActionEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String runId;
  private String toolCallId;
  private String actionType;
  private String requestJson;
  private String responseJson;
  private String resolutionDigest;
  private String status;
  private Instant expiresAt;
  private String resolvedBy;
  private Instant resolvedAt;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;

  public static AgentPendingActionEntity fromDomain(AgentPendingAction a) {
    AgentPendingActionEntity e = new AgentPendingActionEntity();
    e.id = a.id();
    e.tenantId = a.tenantId();
    e.runId = a.runId();
    e.toolCallId = a.toolCallId();
    e.actionType = a.actionType().dbValue();
    e.requestJson = a.requestJson();
    e.responseJson = a.responseJson();
    e.resolutionDigest = a.resolutionDigest();
    e.status = a.status().dbValue();
    e.expiresAt = a.expiresAt();
    e.resolvedBy = a.resolvedBy();
    e.resolvedAt = a.resolvedAt();
    e.revision = a.revision();
    e.createdAt = a.createdAt();
    e.updatedAt = a.updatedAt();
    return e;
  }

  public AgentPendingAction toDomain() {
    return new AgentPendingAction(
        id,
        tenantId,
        runId,
        toolCallId,
        PendingActionType.fromDbValue(actionType),
        requestJson,
        responseJson,
        resolutionDigest,
        PendingActionStatus.fromDbValue(status),
        expiresAt,
        resolvedBy,
        resolvedAt,
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

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }

  public String getToolCallId() {
    return toolCallId;
  }

  public void setToolCallId(String toolCallId) {
    this.toolCallId = toolCallId;
  }

  public String getActionType() {
    return actionType;
  }

  public void setActionType(String actionType) {
    this.actionType = actionType;
  }

  public String getRequestJson() {
    return requestJson;
  }

  public void setRequestJson(String requestJson) {
    this.requestJson = requestJson;
  }

  public String getResponseJson() {
    return responseJson;
  }

  public void setResponseJson(String responseJson) {
    this.responseJson = responseJson;
  }

  public String getResolutionDigest() {
    return resolutionDigest;
  }

  public void setResolutionDigest(String resolutionDigest) {
    this.resolutionDigest = resolutionDigest;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }

  public String getResolvedBy() {
    return resolvedBy;
  }

  public void setResolvedBy(String resolvedBy) {
    this.resolvedBy = resolvedBy;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
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
