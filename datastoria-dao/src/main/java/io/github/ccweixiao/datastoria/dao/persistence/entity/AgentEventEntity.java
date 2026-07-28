package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.agent.PersistedAgentFrame;

/** Database row POJO for {@code ds_agent_event} (append-only SSE replay frames). */
@TableName("ds_agent_event")
public class AgentEventEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String tenantId;
  private String runId;
  private Long sequence;
  private String frameText;
  private Instant createdAt;

  public static AgentEventEntity fromDomain(PersistedAgentFrame f) {
    AgentEventEntity e = new AgentEventEntity();
    e.id = f.id();
    e.tenantId = f.tenantId();
    e.runId = f.runId();
    e.sequence = f.sequence();
    e.frameText = f.frameText();
    e.createdAt = f.createdAt();
    return e;
  }

  public PersistedAgentFrame toDomain() {
    return new PersistedAgentFrame(
        id, tenantId, runId, sequence != null ? sequence : 0L, frameText, createdAt);
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

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(Long sequence) {
    this.sequence = sequence;
  }

  public String getFrameText() {
    return frameText;
  }

  public void setFrameText(String frameText) {
    this.frameText = frameText;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
