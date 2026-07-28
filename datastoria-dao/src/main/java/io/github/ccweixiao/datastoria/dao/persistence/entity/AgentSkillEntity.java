package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.AgentSkill;

/**
 * Database row POJO for {@code ds_agent_skill}. The read queries
 * (findVisible/findById/findRevision) JOIN {@code ds_skill_revision} and alias its columns onto
 * this entity's fields ({@code skill_md -> content}, {@code effective_state -> state}, {@code
 * skill_version -> version}, {@code skill_revision -> revision}, {@code content_checksum ->
 * bundleChecksum}); the read {@code resultMap} in the XML performs that mapping. Composite key
 * {@code (tenant_id, id)}.
 */
@TableName("ds_agent_skill")
public class AgentSkillEntity {

  private String id;
  private String tenantId;
  private String ownerUserId;
  private String content;
  private String state;
  private String scope;
  private String version;
  private String bundleChecksum;
  private Boolean builtin;
  private Long revision;
  private Long publishedRevision;
  private Long draftRevision;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant deletedAt;

  public AgentSkill toDomain() {
    return new AgentSkill(
        id,
        tenantId,
        ownerUserId,
        content,
        state,
        scope,
        version,
        bundleChecksum,
        builtin != null && builtin,
        revision != null ? revision : 0L,
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

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getBundleChecksum() {
    return bundleChecksum;
  }

  public void setBundleChecksum(String bundleChecksum) {
    this.bundleChecksum = bundleChecksum;
  }

  public Boolean getBuiltin() {
    return builtin;
  }

  public void setBuiltin(Boolean builtin) {
    this.builtin = builtin;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public Long getPublishedRevision() {
    return publishedRevision;
  }

  public void setPublishedRevision(Long publishedRevision) {
    this.publishedRevision = publishedRevision;
  }

  public Long getDraftRevision() {
    return draftRevision;
  }

  public void setDraftRevision(Long draftRevision) {
    this.draftRevision = draftRevision;
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
