package io.datastoria.server.repository.jdbc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.ResourceInUseException;
import io.datastoria.server.domain.AgentSkill;
import io.datastoria.server.domain.AgentSkillResource;
import io.datastoria.server.repository.AgentSkillRepository;
import io.datastoria.server.skill.SkillMetadataParser;

@Repository
public class JdbcAgentSkillRepository implements AgentSkillRepository {

  private static final RowMapper<AgentSkill> SKILL_MAPPER =
      (rs, rowNum) ->
          new AgentSkill(
              rs.getString("id"),
              rs.getString("tenant_id"),
              rs.getString("owner_user_id"),
              rs.getString("skill_md"),
              rs.getString("effective_state"),
              rs.getString("scope"),
              rs.getString("skill_version"),
              rs.getString("content_checksum"),
              rs.getBoolean("builtin"),
              rs.getLong("skill_revision"),
              SqlTimestamps.fromParam(rs, "created_at"),
              SqlTimestamps.fromParam(rs, "updated_at"),
              SqlTimestamps.fromParam(rs, "deleted_at"));

  private static final RowMapper<AgentSkillResource> RESOURCE_MAPPER =
      (rs, rowNum) ->
          new AgentSkillResource(
              rs.getString("tenant_id"),
              rs.getString("skill_id"),
              rs.getString("resource_path"),
              rs.getString("content"),
              null,
              null);

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;
  private final SkillMetadataParser metadataParser;

  public JdbcAgentSkillRepository(
      JdbcClient jdbc, ObjectMapper objectMapper, SkillMetadataParser metadataParser) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.metadataParser = metadataParser;
  }

  @Override
  public List<AgentSkill> findVisible(String tenantId, String userId, boolean includeDraft) {
    return jdbc.sql(
            """
            SELECT s.id, s.tenant_id, s.owner_user_id, s.scope, s.builtin,
                   s.created_at, s.updated_at, s.deleted_at,
                   r.revision AS skill_revision, r.version AS skill_version,
                   r.skill_md, r.content_checksum,
                   CASE WHEN :includeDraft = 1 AND s.draft_revision IS NOT NULL
                        THEN 'draft' ELSE 'published' END AS effective_state
            FROM ds_agent_skill s
            JOIN ds_skill_revision r
              ON r.tenant_id = s.tenant_id AND r.skill_id = s.id
             AND r.revision = CASE
                   WHEN :includeDraft = 1 AND s.draft_revision IS NOT NULL
                     THEN s.draft_revision
                   ELSE s.published_revision
                 END
            WHERE s.tenant_id = :tenantId AND s.deleted_at IS NULL
              AND (s.scope = 'global' OR s.owner_user_id = :userId)
              AND (:includeDraft = 1 OR s.published_revision IS NOT NULL)
            ORDER BY s.id
            """)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("includeDraft", includeDraft ? 1 : 0)
        .query(SKILL_MAPPER)
        .list();
  }

  @Override
  public Optional<AgentSkill> findById(
      String tenantId, String userId, String id, boolean includeDraft) {
    return jdbc.sql(
            """
            SELECT s.id, s.tenant_id, s.owner_user_id, s.scope, s.builtin,
                   s.created_at, s.updated_at, s.deleted_at,
                   r.revision AS skill_revision, r.version AS skill_version,
                   r.skill_md, r.content_checksum,
                   CASE WHEN :includeDraft = 1 AND s.draft_revision IS NOT NULL
                        THEN 'draft' ELSE 'published' END AS effective_state
            FROM ds_agent_skill s
            JOIN ds_skill_revision r
              ON r.tenant_id = s.tenant_id AND r.skill_id = s.id
             AND r.revision = CASE
                   WHEN :includeDraft = 1 AND s.draft_revision IS NOT NULL
                     THEN s.draft_revision
                   ELSE s.published_revision
                 END
            WHERE s.tenant_id = :tenantId AND s.id = :id AND s.deleted_at IS NULL
              AND (s.scope = 'global' OR s.owner_user_id = :userId)
              AND (:includeDraft = 1 OR s.published_revision IS NOT NULL)
            """)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("id", id)
        .param("includeDraft", includeDraft ? 1 : 0)
        .query(SKILL_MAPPER)
        .optional();
  }

  @Override
  public Optional<AgentSkill> findRevision(
      String tenantId, String userId, String id, long skillRevision) {
    return jdbc.sql(
            """
            SELECT s.id, s.tenant_id, s.owner_user_id, s.scope, s.builtin,
                   s.created_at, s.updated_at, s.deleted_at,
                   r.revision AS skill_revision, r.version AS skill_version,
                   r.skill_md, r.content_checksum, 'pinned' AS effective_state
            FROM ds_agent_skill s
            JOIN ds_skill_revision r
              ON r.tenant_id = s.tenant_id AND r.skill_id = s.id
             AND r.revision = :skillRevision
            WHERE s.tenant_id = :tenantId AND s.id = :id
              AND (s.scope = 'global' OR s.owner_user_id = :userId)
            """)
        .param("tenantId", tenantId)
        .param("userId", userId)
        .param("id", id)
        .param("skillRevision", skillRevision)
        .query(SKILL_MAPPER)
        .optional();
  }

  @Override
  @Transactional
  public AgentSkill saveBundle(AgentSkill skill, List<AgentSkillResource> resources) {
    RootState root = findRoot(skill.tenantId(), skill.id()).orElse(null);
    if (root != null && !root.ownerUserId().equals(skill.ownerUserId())) {
      throw new ResourceInUseException("AgentSkill", skill.id());
    }
    long nextRevision = root == null ? 0 : root.latestRevision() + 1;
    Instant now = Instant.now();
    String checksum =
        skill.bundleChecksum() == null
            ? checksum(skill.content(), resources)
            : skill.bundleChecksum();
    boolean published = "published".equals(skill.state());

    if (root == null) {
      jdbc.sql(
              """
              INSERT INTO ds_agent_skill
                (id, tenant_id, owner_user_id, content, state, scope, version, bundle_checksum,
                 builtin, revision, published_revision, draft_revision, created_at, updated_at)
              VALUES
                (:id, :tenantId, :ownerUserId, :content, :state, :scope, :version,
                 :bundleChecksum, :builtin, :revision, :publishedRevision, :draftRevision,
                 :createdAt, :updatedAt)
              """)
          .param("id", skill.id())
          .param("tenantId", skill.tenantId())
          .param("ownerUserId", skill.ownerUserId())
          .param("content", skill.content())
          .param("state", skill.state())
          .param("scope", skill.scope())
          .param("version", skill.version())
          .param("bundleChecksum", checksum)
          .param("builtin", skill.builtin())
          .param("revision", nextRevision)
          .param("publishedRevision", published ? nextRevision : null)
          .param("draftRevision", published ? null : nextRevision)
          .param("createdAt", SqlTimestamps.toParam(now))
          .param("updatedAt", SqlTimestamps.toParam(now))
          .update();
    } else {
      jdbc.sql(
              """
              UPDATE ds_agent_skill
              SET content = :content, state = :state, scope = :scope, version = :version,
                  bundle_checksum = :bundleChecksum, builtin = :builtin, revision = :revision,
                  published_revision = CASE WHEN :published = 1
                                            THEN :revision ELSE published_revision END,
                  draft_revision = CASE WHEN :published = 1 THEN NULL ELSE :revision END,
                  updated_at = :updatedAt, deleted_at = NULL
              WHERE tenant_id = :tenantId AND id = :id AND owner_user_id = :ownerUserId
              """)
          .param("content", skill.content())
          .param("state", skill.state())
          .param("scope", skill.scope())
          .param("version", skill.version())
          .param("bundleChecksum", checksum)
          .param("builtin", skill.builtin())
          .param("revision", nextRevision)
          .param("published", published ? 1 : 0)
          .param("updatedAt", SqlTimestamps.toParam(now))
          .param("tenantId", skill.tenantId())
          .param("id", skill.id())
          .param("ownerUserId", skill.ownerUserId())
          .update();
    }

    var metadata = metadataParser.parse(skill.content(), skill.id());
    jdbc.sql(
            """
            INSERT INTO ds_skill_revision
              (tenant_id, skill_id, revision, version, name, description, summary, skill_md,
               metadata_json, required_tools_json, content_checksum, review_status,
               created_by, created_at)
            VALUES
              (:tenantId, :skillId, :revision, :version, :name, :description, :summary, :skillMd,
               :metadataJson, :requiredToolsJson, :checksum, :reviewStatus, :createdBy, :createdAt)
            """)
        .param("tenantId", skill.tenantId())
        .param("skillId", skill.id())
        .param("revision", nextRevision)
        .param("version", skill.version())
        .param("name", metadata.name())
        .param("description", metadata.description())
        .param("summary", metadata.summary())
        .param("skillMd", skill.content())
        .param("metadataJson", metadataJson(metadata))
        .param("requiredToolsJson", json(metadata.requiredTools()))
        .param("checksum", checksum)
        .param("reviewStatus", skill.builtin() ? "not_required" : "pending")
        .param("createdBy", skill.ownerUserId())
        .param("createdAt", SqlTimestamps.toParam(now))
        .update();

    for (AgentSkillResource resource : resources) {
      byte[] content = resource.content().getBytes(StandardCharsets.UTF_8);
      jdbc.sql(
              """
              INSERT INTO ds_skill_resource
                (tenant_id, skill_id, skill_revision, resource_path, media_type, content,
                 size_bytes, checksum)
              VALUES
                (:tenantId, :skillId, :skillRevision, :path, :mediaType, :content,
                 :sizeBytes, :checksum)
              """)
          .param("tenantId", skill.tenantId())
          .param("skillId", skill.id())
          .param("skillRevision", nextRevision)
          .param("path", resource.path())
          .param("mediaType", "text/plain; charset=utf-8")
          .param("content", resource.content())
          .param("sizeBytes", content.length)
          .param("checksum", sha256(content))
          .update();
    }

    syncCompatibilityResources(skill.tenantId(), skill.id(), resources, now);
    return findById(skill.tenantId(), skill.ownerUserId(), skill.id(), !published).orElseThrow();
  }

  @Override
  public List<AgentSkillResource> findResources(
      String tenantId, String skillId, long skillRevision) {
    return jdbc.sql(
            """
            SELECT tenant_id, skill_id, resource_path, content
            FROM ds_skill_resource
            WHERE tenant_id = :tenantId AND skill_id = :skillId
              AND skill_revision = :skillRevision
            ORDER BY resource_path
            """)
        .param("tenantId", tenantId)
        .param("skillId", skillId)
        .param("skillRevision", skillRevision)
        .query(RESOURCE_MAPPER)
        .list();
  }

  @Override
  public void publish(String tenantId, String userId, String id) {
    int updated =
        jdbc.sql(
                """
                UPDATE ds_agent_skill
                SET state = 'published', published_revision = draft_revision,
                    draft_revision = NULL, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND id = :id AND owner_user_id = :userId
                  AND draft_revision IS NOT NULL AND deleted_at IS NULL
                """)
            .param("updatedAt", SqlTimestamps.toParam(Instant.now()))
            .param("tenantId", tenantId)
            .param("id", id)
            .param("userId", userId)
            .update();
    if (updated == 0) {
      throw new NotFoundException("AgentSkillDraft", id);
    }
  }

  @Override
  public void delete(String tenantId, String userId, String id) {
    int updated =
        jdbc.sql(
                """
                UPDATE ds_agent_skill SET deleted_at = :deletedAt
                WHERE tenant_id = :tenantId AND id = :id AND owner_user_id = :userId
                  AND builtin = FALSE AND deleted_at IS NULL
                """)
            .param("deletedAt", SqlTimestamps.toParam(Instant.now()))
            .param("tenantId", tenantId)
            .param("id", id)
            .param("userId", userId)
            .update();
    if (updated == 0) {
      throw new NotFoundException("AgentSkill", id);
    }
  }

  private Optional<RootState> findRoot(String tenantId, String id) {
    return jdbc.sql(
            """
            SELECT owner_user_id, revision FROM ds_agent_skill
            WHERE tenant_id = :tenantId AND id = :id
            """)
        .param("tenantId", tenantId)
        .param("id", id)
        .query((rs, rowNum) -> new RootState(rs.getString("owner_user_id"), rs.getLong("revision")))
        .optional();
  }

  private void syncCompatibilityResources(
      String tenantId, String skillId, List<AgentSkillResource> resources, Instant now) {
    jdbc.sql(
            "DELETE FROM ds_agent_skill_resource WHERE tenant_id = :tenantId AND skill_id = :skillId")
        .param("tenantId", tenantId)
        .param("skillId", skillId)
        .update();
    for (AgentSkillResource resource : resources) {
      jdbc.sql(
              """
              INSERT INTO ds_agent_skill_resource
                (tenant_id, skill_id, resource_path, content, created_at, updated_at)
              VALUES (:tenantId, :skillId, :path, :content, :createdAt, :updatedAt)
              """)
          .param("tenantId", tenantId)
          .param("skillId", skillId)
          .param("path", resource.path())
          .param("content", resource.content())
          .param("createdAt", SqlTimestamps.toParam(now))
          .param("updatedAt", SqlTimestamps.toParam(now))
          .update();
    }
  }

  private String metadataJson(SkillMetadataParser.ParsedSkillMetadata metadata) {
    return json(
        java.util.Map.of(
            "author",
            metadata.author() == null ? "" : metadata.author(),
            "url",
            metadata.url() == null ? "" : metadata.url(),
            "disableSlashCommand",
            metadata.disableSlashCommand(),
            "showInSqlEditorQuickAction",
            metadata.showInSqlEditorQuickAction()));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException impossible) {
      throw new IllegalStateException("Unable to serialize Skill metadata", impossible);
    }
  }

  private static String checksum(String skillMarkdown, List<AgentSkillResource> resources) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      updateDigest(digest, "SKILL.md", skillMarkdown.getBytes(StandardCharsets.UTF_8));
      resources.stream()
          .sorted(Comparator.comparing(AgentSkillResource::path))
          .forEach(
              resource ->
                  updateDigest(
                      digest,
                      resource.path(),
                      resource.content().getBytes(StandardCharsets.UTF_8)));
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static void updateDigest(MessageDigest digest, String path, byte[] content) {
    digest.update(path.getBytes(StandardCharsets.UTF_8));
    digest.update((byte) 0);
    digest.update(content);
    digest.update((byte) 0);
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private record RootState(String ownerUserId, long latestRevision) {}
}
