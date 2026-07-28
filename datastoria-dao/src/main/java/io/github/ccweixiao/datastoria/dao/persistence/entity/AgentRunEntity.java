package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.agent.AgentRun;
import io.github.ccweixiao.datastoria.common.agent.AgentRunStatus;

/**
 * Database row POJO for {@code ds_agent_run}. {@code status} is stored as the lowercase enum name.
 */
@TableName("ds_agent_run")
public class AgentRunEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String userId;
  private String sessionId;
  private String messageId;
  private String agentRevisionId;
  private String modelId;
  private String status;
  private String idempotencyKey;
  private String requestId;
  private String connectionId;
  private String inputSnapshotJson;
  private String usageJson;
  private String errorCode;
  private String safeMessage;
  private Long revision;
  private Instant startedAt;
  private Instant finishedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public static AgentRunEntity fromDomain(AgentRun r) {
    AgentRunEntity e = new AgentRunEntity();
    e.id = r.id();
    e.tenantId = r.tenantId();
    e.userId = r.userId();
    e.sessionId = r.sessionId();
    e.messageId = r.messageId();
    e.agentRevisionId = r.agentRevisionId();
    e.modelId = r.modelId();
    e.status = r.status().dbValue();
    e.idempotencyKey = r.idempotencyKey();
    e.requestId = r.requestId();
    e.connectionId = r.connectionId();
    e.inputSnapshotJson = r.inputSnapshotJson();
    e.usageJson = r.usageJson();
    e.errorCode = r.errorCode();
    e.safeMessage = r.safeMessage();
    e.revision = r.revision();
    e.startedAt = r.startedAt();
    e.finishedAt = r.finishedAt();
    e.createdAt = r.createdAt();
    e.updatedAt = r.updatedAt();
    return e;
  }

  public AgentRun toDomain() {
    return new AgentRun(
        id,
        tenantId,
        userId,
        sessionId,
        messageId,
        agentRevisionId,
        modelId,
        AgentRunStatus.fromDbValue(status),
        idempotencyKey,
        requestId,
        connectionId,
        inputSnapshotJson,
        usageJson,
        errorCode,
        safeMessage,
        revision != null ? revision : 0L,
        startedAt,
        finishedAt,
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

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  public String getAgentRevisionId() {
    return agentRevisionId;
  }

  public void setAgentRevisionId(String agentRevisionId) {
    this.agentRevisionId = agentRevisionId;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public void setConnectionId(String connectionId) {
    this.connectionId = connectionId;
  }

  public String getInputSnapshotJson() {
    return inputSnapshotJson;
  }

  public void setInputSnapshotJson(String inputSnapshotJson) {
    this.inputSnapshotJson = inputSnapshotJson;
  }

  public String getUsageJson() {
    return usageJson;
  }

  public void setUsageJson(String usageJson) {
    this.usageJson = usageJson;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getSafeMessage() {
    return safeMessage;
  }

  public void setSafeMessage(String safeMessage) {
    this.safeMessage = safeMessage;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
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
