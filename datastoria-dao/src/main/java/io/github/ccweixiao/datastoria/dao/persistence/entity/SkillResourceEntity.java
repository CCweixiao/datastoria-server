package io.github.ccweixiao.datastoria.dao.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.domain.AgentSkillResource;

/**
 * Database row POJO for {@code ds_skill_resource}. findResources populates only the display
 * columns.
 */
@TableName("ds_skill_resource")
public class SkillResourceEntity {

  private String tenantId;
  private String skillId;
  private Long skillRevision;
  private String resourcePath;
  private String mediaType;
  private String content;
  private Long sizeBytes;
  private String checksum;

  public AgentSkillResource toDomain() {
    return new AgentSkillResource(tenantId, skillId, resourcePath, content, null, null);
  }

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

  public Long getSkillRevision() {
    return skillRevision;
  }

  public void setSkillRevision(Long skillRevision) {
    this.skillRevision = skillRevision;
  }

  public String getResourcePath() {
    return resourcePath;
  }

  public void setResourcePath(String resourcePath) {
    this.resourcePath = resourcePath;
  }

  public String getMediaType() {
    return mediaType;
  }

  public void setMediaType(String mediaType) {
    this.mediaType = mediaType;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(Long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(String checksum) {
    this.checksum = checksum;
  }
}
