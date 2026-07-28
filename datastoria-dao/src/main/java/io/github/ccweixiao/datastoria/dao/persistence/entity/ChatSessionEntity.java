package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.ChatSession;

/** Database row POJO for {@code ds_chat_session} (hard-deleted; no {@code deleted_at}). */
@TableName("ds_chat_session")
public class ChatSessionEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String userId;
  private String connectionId;
  private String title;
  private Long revision;
  private Instant createdAt;
  private Instant updatedAt;

  public static ChatSessionEntity fromDomain(ChatSession s) {
    ChatSessionEntity e = new ChatSessionEntity();
    e.id = s.id();
    e.tenantId = s.tenantId();
    e.userId = s.userId();
    e.connectionId = s.connectionId();
    e.title = s.title();
    e.revision = s.revision();
    e.createdAt = s.createdAt();
    e.updatedAt = s.updatedAt();
    return e;
  }

  public ChatSession toDomain() {
    return new ChatSession(
        id,
        tenantId,
        userId,
        connectionId,
        title,
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

  public String getConnectionId() {
    return connectionId;
  }

  public void setConnectionId(String connectionId) {
    this.connectionId = connectionId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
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
