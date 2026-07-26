package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.AgentDefinition;

/**
 * Database row POJO for {@code ds_agent_definition}. The {@code active_key} generated column is
 * absent.
 */
@TableName("ds_agent_definition")
public class AgentDefinitionEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String agentKey;
  private String name;
  private String description;
  private String status;
  private String publishedRevisionId;
  private Long revision;
  private String createdBy;
  private String updatedBy;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public static AgentDefinitionEntity fromDomain(AgentDefinition d) {
    AgentDefinitionEntity e = new AgentDefinitionEntity();
    e.id = d.id();
    e.tenantId = d.tenantId();
    e.agentKey = d.agentKey();
    e.name = d.name();
    e.description = d.description();
    e.status = d.status();
    e.publishedRevisionId = d.publishedRevisionId();
    e.revision = d.revision();
    e.createdBy = d.createdBy();
    e.updatedBy = d.updatedBy();
    e.createdAt = d.createdAt();
    e.updatedAt = d.updatedAt();
    e.deletedAt = d.deletedAt();
    return e;
  }

  public AgentDefinition toDomain() {
    return new AgentDefinition(
        id,
        tenantId,
        agentKey,
        name,
        description,
        status,
        publishedRevisionId,
        revision != null ? revision : 0L,
        createdBy,
        updatedBy,
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

  public String getAgentKey() {
    return agentKey;
  }

  public void setAgentKey(String agentKey) {
    this.agentKey = agentKey;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getPublishedRevisionId() {
    return publishedRevisionId;
  }

  public void setPublishedRevisionId(String publishedRevisionId) {
    this.publishedRevisionId = publishedRevisionId;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(String updatedBy) {
    this.updatedBy = updatedBy;
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
