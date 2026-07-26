package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.FeedbackEvent;

/** Database row POJO for {@code ds_feedback_event}. */
@TableName("ds_feedback_event")
public class FeedbackEventEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String userId;
  private String source;
  private String sessionId;
  private String messageId;
  private Boolean solved;
  private String reasonCode;
  private String payloadJson;
  private String freeText;
  private Boolean recoveryActionTaken;
  private Instant createdAt;
  private Instant updatedAt;

  public static FeedbackEventEntity fromDomain(FeedbackEvent f) {
    FeedbackEventEntity e = new FeedbackEventEntity();
    e.id = f.id();
    e.tenantId = f.tenantId();
    e.userId = f.userId();
    e.source = f.source();
    e.sessionId = f.sessionId();
    e.messageId = f.messageId();
    e.solved = f.solved();
    e.reasonCode = f.reasonCode();
    e.payloadJson = f.payloadJson();
    e.freeText = f.freeText();
    e.recoveryActionTaken = f.recoveryActionTaken();
    e.createdAt = f.createdAt();
    e.updatedAt = f.updatedAt();
    return e;
  }

  public FeedbackEvent toDomain() {
    return new FeedbackEvent(
        id,
        tenantId,
        userId,
        source,
        sessionId,
        messageId,
        solved != null && solved,
        reasonCode,
        payloadJson,
        freeText,
        recoveryActionTaken != null && recoveryActionTaken,
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

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
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

  public Boolean getSolved() {
    return solved;
  }

  public void setSolved(Boolean solved) {
    this.solved = solved;
  }

  public String getReasonCode() {
    return reasonCode;
  }

  public void setReasonCode(String reasonCode) {
    this.reasonCode = reasonCode;
  }

  public String getPayloadJson() {
    return payloadJson;
  }

  public void setPayloadJson(String payloadJson) {
    this.payloadJson = payloadJson;
  }

  public String getFreeText() {
    return freeText;
  }

  public void setFreeText(String freeText) {
    this.freeText = freeText;
  }

  public Boolean getRecoveryActionTaken() {
    return recoveryActionTaken;
  }

  public void setRecoveryActionTaken(Boolean recoveryActionTaken) {
    this.recoveryActionTaken = recoveryActionTaken;
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
