package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.ChatMessage;

/** Database row POJO for {@code ds_chat_message}. */
@TableName("ds_chat_message")
public class ChatMessageEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String sessionId;
  private String userId;
  private String role;
  private String partsJson;
  private String metadataJson;
  private Long sequence;
  private Instant createdAt;
  private Instant updatedAt;

  public static ChatMessageEntity fromDomain(ChatMessage m) {
    ChatMessageEntity e = new ChatMessageEntity();
    e.id = m.id();
    e.tenantId = m.tenantId();
    e.sessionId = m.sessionId();
    e.userId = m.userId();
    e.role = m.role();
    e.partsJson = m.partsJson();
    e.metadataJson = m.metadataJson();
    e.sequence = m.sequence();
    e.createdAt = m.createdAt();
    e.updatedAt = m.updatedAt();
    return e;
  }

  public ChatMessage toDomain() {
    return new ChatMessage(
        id,
        tenantId,
        sessionId,
        userId,
        role,
        partsJson,
        metadataJson,
        sequence != null ? sequence : 0L,
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

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getPartsJson() {
    return partsJson;
  }

  public void setPartsJson(String partsJson) {
    this.partsJson = partsJson;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(Long sequence) {
    this.sequence = sequence;
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
