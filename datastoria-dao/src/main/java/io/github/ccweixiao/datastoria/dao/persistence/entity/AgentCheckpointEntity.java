package io.github.ccweixiao.datastoria.dao.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.TableName;

import io.github.ccweixiao.datastoria.common.agent.AgentCheckpoint;
import io.github.ccweixiao.datastoria.common.agent.CheckpointType;

/**
 * Database row POJO for {@code ds_agent_checkpoint}. Composite natural key {@code (tenant_id,
 * run_id, sequence)}; {@code checkpoint_type} is stored as the lowercase enum name.
 */
@TableName("ds_agent_checkpoint")
public class AgentCheckpointEntity {

  private String id;
  private String tenantId;
  private String runId;
  private Long sequence;
  private String checkpointType;
  private String stateJson;
  private String codecVersion;
  private String checksum;
  private Instant createdAt;
  private Instant updatedAt;

  public static AgentCheckpointEntity fromDomain(AgentCheckpoint c) {
    AgentCheckpointEntity e = new AgentCheckpointEntity();
    e.id = c.id();
    e.tenantId = c.tenantId();
    e.runId = c.runId();
    e.sequence = c.sequence();
    e.checkpointType = c.checkpointType().dbValue();
    e.stateJson = c.stateJson();
    e.codecVersion = c.codecVersion();
    e.checksum = c.checksum();
    e.createdAt = c.createdAt();
    e.updatedAt = c.updatedAt();
    return e;
  }

  public AgentCheckpoint toDomain() {
    return new AgentCheckpoint(
        id,
        tenantId,
        runId,
        sequence != null ? sequence : 0L,
        CheckpointType.fromDbValue(checkpointType),
        stateJson,
        codecVersion,
        checksum,
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

  public String getCheckpointType() {
    return checkpointType;
  }

  public void setCheckpointType(String checkpointType) {
    this.checkpointType = checkpointType;
  }

  public String getStateJson() {
    return stateJson;
  }

  public void setStateJson(String stateJson) {
    this.stateJson = stateJson;
  }

  public String getCodecVersion() {
    return codecVersion;
  }

  public void setCodecVersion(String codecVersion) {
    this.codecVersion = codecVersion;
  }

  public String getChecksum() {
    return checksum;
  }

  public void setChecksum(String checksum) {
    this.checksum = checksum;
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
