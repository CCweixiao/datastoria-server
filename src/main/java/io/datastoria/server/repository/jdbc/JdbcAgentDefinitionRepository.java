package io.datastoria.server.repository.jdbc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.AgentDefinition;
import io.datastoria.server.repository.AgentDefinitionRepository;

@Repository
public class JdbcAgentDefinitionRepository implements AgentDefinitionRepository {

  private static final RowMapper<AgentDefinition> MAPPER =
      (rs, rowNum) ->
          new AgentDefinition(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("agent_key"),
              rs.getString("name"),
              rs.getString("description"),
              rs.getString("status"),
              rs.getString("published_revision_id"),
              rs.getLong("revision"),
              rs.getString("created_by"),
              rs.getString("updated_by"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private final JdbcClient jdbc;

  public JdbcAgentDefinitionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AgentDefinition save(AgentDefinition d) {
    Instant now = Instant.now();
    jdbc.sql(
            "INSERT INTO ds_agent_definition (id, tenant_id, agent_key, name, description,"
                + " status, published_revision_id, revision, created_by, updated_by,"
                + " created_at, updated_at)"
                + " VALUES (:id, :tenantId, :agentKey, :name, :description, :status,"
                + " :publishedRevisionId, 0, :createdBy, :updatedBy, :createdAt, :updatedAt)")
        .param("id", d.id())
        .param("tenantId", d.tenantId())
        .param("agentKey", d.agentKey())
        .param("name", d.name())
        .param("description", d.description())
        .param("status", d.status())
        .param("publishedRevisionId", d.publishedRevisionId())
        .param("createdBy", d.createdBy())
        .param("updatedBy", d.updatedBy())
        .param("createdAt", SqlTimestamps.toParam(now))
        .param("updatedAt", SqlTimestamps.toParam(now))
        .update();
    return findById(d.id(), d.tenantId())
        .orElseThrow(() -> new NotFoundException("AgentDefinition", d.id()));
  }

  @Override
  public Optional<AgentDefinition> findById(String id, String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_definition WHERE id = :id AND tenant_id = :tenantId"
                + " AND deleted_at IS NULL")
        .param("id", id)
        .param("tenantId", tenantId)
        .query(MAPPER)
        .optional();
  }

  @Override
  public List<AgentDefinition> findAll(String tenantId) {
    return jdbc.sql(
            "SELECT * FROM ds_agent_definition WHERE tenant_id = :tenantId AND deleted_at IS NULL")
        .param("tenantId", tenantId)
        .query(MAPPER)
        .list();
  }

  @Override
  public AgentDefinition update(AgentDefinition d, long expectedRevision) {
    Instant now = Instant.now();
    int affected =
        jdbc.sql(
                "UPDATE ds_agent_definition SET name = :name, description = :description,"
                    + " updated_by = :updatedBy, updated_at = :updatedAt,"
                    + " revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("name", d.name())
            .param("description", d.description())
            .param("updatedBy", d.updatedBy())
            .param("updatedAt", SqlTimestamps.toParam(now))
            .param("id", d.id())
            .param("tenantId", d.tenantId())
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(d.id(), d.tenantId()).isEmpty()) {
        throw new NotFoundException("AgentDefinition", d.id());
      }
      throw new RevisionConflictException("AgentDefinition", d.id(), expectedRevision, -1);
    }
    return findById(d.id(), d.tenantId()).orElseThrow();
  }

  @Override
  public void publish(String id, String tenantId, String revisionId, long expectedRevision) {
    int affected =
        jdbc.sql(
                "UPDATE ds_agent_definition SET published_revision_id = :revisionId,"
                    + " status = 'published', updated_at = :now,"
                    + " revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("revisionId", revisionId)
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("AgentDefinition", id);
      }
      throw new RevisionConflictException("AgentDefinition", id, expectedRevision, -1);
    }
  }

  @Override
  public void disable(String id, String tenantId, long expectedRevision) {
    int affected =
        jdbc.sql(
                "UPDATE ds_agent_definition SET status = 'disabled',"
                    + " updated_at = :now, revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("AgentDefinition", id);
      }
      throw new RevisionConflictException("AgentDefinition", id, expectedRevision, -1);
    }
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int affected =
        jdbc.sql(
                "UPDATE ds_agent_definition SET deleted_at = :now, revision = revision + 1"
                    + " WHERE id = :id AND tenant_id = :tenantId"
                    + " AND revision = :expectedRevision AND deleted_at IS NULL")
            .param("now", SqlTimestamps.toParam(Instant.now()))
            .param("id", id)
            .param("tenantId", tenantId)
            .param("expectedRevision", expectedRevision)
            .update();
    if (affected == 0) {
      if (findById(id, tenantId).isEmpty()) {
        throw new NotFoundException("AgentDefinition", id);
      }
      throw new RevisionConflictException("AgentDefinition", id, expectedRevision, -1);
    }
  }
}
