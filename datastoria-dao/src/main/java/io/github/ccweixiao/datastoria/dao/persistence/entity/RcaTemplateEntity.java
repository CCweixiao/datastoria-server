package io.github.ccweixiao.datastoria.dao.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * Database row POJO for {@code ds_rca_template}. Unlike the domain tables, {@code
 * created_at}/{@code updated_at} are BIGINT epoch-millis (not ISO TEXT/datetime), so they are
 * {@code Long} here and do NOT use the {@link
 * io.github.ccweixiao.datastoria.dao.persistence.typehandler.InstantTypeHandler}.
 */
@TableName("ds_rca_template")
public class RcaTemplateEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String templateKey;
  private String sourceYaml;
  private Boolean enabled;
  private Long revision;
  private Long createdAt;
  private Long updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public void setTemplateKey(String templateKey) {
    this.templateKey = templateKey;
  }

  public String getSourceYaml() {
    return sourceYaml;
  }

  public void setSourceYaml(String sourceYaml) {
    this.sourceYaml = sourceYaml;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Long getRevision() {
    return revision;
  }

  public void setRevision(Long revision) {
    this.revision = revision;
  }

  public Long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Long createdAt) {
    this.createdAt = createdAt;
  }

  public Long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Long updatedAt) {
    this.updatedAt = updatedAt;
  }
}
