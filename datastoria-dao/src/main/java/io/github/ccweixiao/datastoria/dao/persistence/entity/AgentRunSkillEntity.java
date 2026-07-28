package io.github.ccweixiao.datastoria.dao.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.agent.AgentRunSkillPin;

/** Database row POJO for {@code ds_agent_run_skill} (composite key, no surrogate id). */
@TableName("ds_agent_run_skill")
public class AgentRunSkillEntity {

  private String tenantId;
  private String runId;
  private String skillId;
  private Long skillRevision;
  private String contentChecksum;

  public static AgentRunSkillEntity fromDomain(AgentRunSkillPin p) {
    AgentRunSkillEntity e = new AgentRunSkillEntity();
    e.tenantId = p.tenantId();
    e.runId = p.runId();
    e.skillId = p.skillId();
    e.skillRevision = p.skillRevision();
    e.contentChecksum = p.contentChecksum();
    return e;
  }

  public AgentRunSkillPin toDomain() {
    return new AgentRunSkillPin(
        tenantId, runId, skillId, skillRevision != null ? skillRevision : 0L, contentChecksum);
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

  public String getContentChecksum() {
    return contentChecksum;
  }

  public void setContentChecksum(String contentChecksum) {
    this.contentChecksum = contentChecksum;
  }
}
