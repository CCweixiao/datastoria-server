package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Database row POJO for {@code ds_skill_revision} (composite key {@code tenant_id, skill_id,
 * revision}).
 */
@TableName("ds_skill_revision")
public class SkillRevisionEntity {

  private String tenantId;
  private String skillId;
  private Long revision;
  private String version;
  private String name;
  private String description;
  private String summary;
  private String skillMd;
  private String metadataJson;
  private String requiredToolsJson;
  private String contentChecksum;
  private String reviewStatus;
  private String createdBy;
  private Instant createdAt;

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getSkillId() {
    return skillId;
  }

  public void setSkillId(String skillId) {
    this.skillId = skillId;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
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

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getSkillMd() {
    return skillMd;
  }

  public void setSkillMd(String skillMd) {
    this.skillMd = skillMd;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public void setMetadataJson(String metadataJson) {
    this.metadataJson = metadataJson;
  }

  public String getRequiredToolsJson() {
    return requiredToolsJson;
  }

  public void setRequiredToolsJson(String requiredToolsJson) {
    this.requiredToolsJson = requiredToolsJson;
  }

  public String getContentChecksum() {
    return contentChecksum;
  }

  public void setContentChecksum(String contentChecksum) {
    this.contentChecksum = contentChecksum;
  }

  public String getReviewStatus() {
    return reviewStatus;
  }

  public void setReviewStatus(String reviewStatus) {
    this.reviewStatus = reviewStatus;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
