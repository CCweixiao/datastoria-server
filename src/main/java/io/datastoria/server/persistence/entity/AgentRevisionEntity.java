package io.datastoria.server.persistence.entity;

import java.time.Instant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.datastoria.server.domain.AgentRevision;

/**
 * Database row POJO for {@code ds_agent_revision}. This table has no {@code tenant_id}; tenant
 * scoping is applied via a JOIN to {@code ds_agent_definition} in the mapper XML.
 */
@TableName("ds_agent_revision")
public class AgentRevisionEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private String id;

  private String agentId;
  private Integer version;
  private String modelId;
  private String systemPrompt;
  private String promptChecksum;
  private String runtimeConfigJson;
  private String toolPolicyJson;
  private String skillPolicyJson;
  private String createdBy;
  private Instant createdAt;

  public static AgentRevisionEntity fromDomain(AgentRevision r) {
    AgentRevisionEntity e = new AgentRevisionEntity();
    e.id = r.id();
    e.agentId = r.agentId();
    e.version = r.version();
    e.modelId = r.modelId();
    e.systemPrompt = r.systemPrompt();
    e.promptChecksum = r.promptChecksum();
    e.runtimeConfigJson = r.runtimeConfigJson();
    e.toolPolicyJson = r.toolPolicyJson();
    e.skillPolicyJson = r.skillPolicyJson();
    e.createdBy = r.createdBy();
    e.createdAt = r.createdAt();
    return e;
  }

  public AgentRevision toDomain() {
    return new AgentRevision(
        id,
        agentId,
        version != null ? version : 0,
        modelId,
        systemPrompt,
        promptChecksum,
        runtimeConfigJson,
        toolPolicyJson,
        skillPolicyJson,
        createdBy,
        createdAt);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(String agentId) {
    this.agentId = agentId;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public String getPromptChecksum() {
    return promptChecksum;
  }

  public void setPromptChecksum(String promptChecksum) {
    this.promptChecksum = promptChecksum;
  }

  public String getRuntimeConfigJson() {
    return runtimeConfigJson;
  }

  public void setRuntimeConfigJson(String runtimeConfigJson) {
    this.runtimeConfigJson = runtimeConfigJson;
  }

  public String getToolPolicyJson() {
    return toolPolicyJson;
  }

  public void setToolPolicyJson(String toolPolicyJson) {
    this.toolPolicyJson = toolPolicyJson;
  }

  public String getSkillPolicyJson() {
    return skillPolicyJson;
  }

  public void setSkillPolicyJson(String skillPolicyJson) {
    this.skillPolicyJson = skillPolicyJson;
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
