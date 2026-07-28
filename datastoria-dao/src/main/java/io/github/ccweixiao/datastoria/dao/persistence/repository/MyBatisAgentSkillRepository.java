package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.domain.AgentSkill;
import io.github.ccweixiao.datastoria.common.domain.AgentSkillResource;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.ResourceInUseException;
import io.github.ccweixiao.datastoria.common.skill.SkillMetadataParser;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentSkillEntity;
import io.github.ccweixiao.datastoria.dao.persistence.entity.AgentSkillResourceEntity;
import io.github.ccweixiao.datastoria.dao.persistence.entity.SkillResourceEntity;
import io.github.ccweixiao.datastoria.dao.persistence.entity.SkillRevisionEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.AgentSkillMapper;
import io.github.ccweixiao.datastoria.dao.repository.AgentSkillRepository;

/**
 * MyBatis-Plus adapter for the skill bundle. {@code saveBundle} is transactional and writes the
 * root skill row, an immutable revision, the revision-scoped resources, and the legacy
 * compatibility resource table. The deterministic SHA-256 bundle checksum and metadata
 * serialisation are preserved verbatim from the former JDBC repository.
 */
@Repository
public class MyBatisAgentSkillRepository implements AgentSkillRepository {

  private final AgentSkillMapper mapper;
  private final ObjectMapper objectMapper;
  private final SkillMetadataParser metadataParser;

  public MyBatisAgentSkillRepository(
      AgentSkillMapper mapper, ObjectMapper objectMapper, SkillMetadataParser metadataParser) {
    this.mapper = mapper;
    this.objectMapper = objectMapper;
    this.metadataParser = metadataParser;
  }

  @Override
  public List<AgentSkill> findVisible(String tenantId, String userId, boolean includeDraft) {
    return mapper.findVisible(tenantId, userId, includeDraft ? 1 : 0).stream()
        .map(AgentSkillEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<AgentSkill> findById(
      String tenantId, String userId, String id, boolean includeDraft) {
    return Optional.ofNullable(mapper.findById(tenantId, userId, id, includeDraft ? 1 : 0))
        .map(AgentSkillEntity::toDomain);
  }

  @Override
  public Optional<AgentSkill> findRevision(
      String tenantId, String userId, String id, long skillRevision) {
    return Optional.ofNullable(mapper.findRevision(tenantId, userId, id, skillRevision))
        .map(AgentSkillEntity::toDomain);
  }

  @Override
  @Transactional
  public AgentSkill saveBundle(AgentSkill skill, List<AgentSkillResource> resources) {
    AgentSkillEntity root = mapper.findRoot(skill.tenantId(), skill.id());
    if (root != null && !ownerMatches(root, skill.ownerUserId())) {
      throw new ResourceInUseException("AgentSkill", skill.id());
    }
    long nextRevision = root == null || root.getRevision() == null ? 0 : root.getRevision() + 1;
    Instant now = Instant.now();
    String checksum =
        skill.bundleChecksum() == null
            ? checksum(skill.content(), resources)
            : skill.bundleChecksum();
    boolean published = "published".equals(skill.state());

    AgentSkillEntity row = new AgentSkillEntity();
    row.setId(skill.id());
    row.setTenantId(skill.tenantId());
    row.setOwnerUserId(skill.ownerUserId());
    row.setContent(skill.content());
    row.setState(skill.state());
    row.setScope(skill.scope());
    row.setVersion(skill.version());
    row.setBundleChecksum(checksum);
    row.setBuiltin(skill.builtin());
    row.setRevision(nextRevision);
    row.setPublishedRevision(published ? nextRevision : null);
    row.setDraftRevision(published ? null : nextRevision);
    row.setCreatedAt(now);
    row.setUpdatedAt(now);

    if (root == null) {
      mapper.insertSkillRoot(row);
    } else {
      mapper.updateSkillRoot(row, published ? 1 : 0);
    }

    SkillMetadataParser.ParsedSkillMetadata metadata =
        metadataParser.parse(skill.content(), skill.id());
    SkillRevisionEntity rev = new SkillRevisionEntity();
    rev.setTenantId(skill.tenantId());
    rev.setSkillId(skill.id());
    rev.setRevision(nextRevision);
    rev.setVersion(skill.version());
    rev.setName(metadata.name());
    rev.setDescription(metadata.description());
    rev.setSummary(metadata.summary());
    rev.setSkillMd(skill.content());
    rev.setMetadataJson(metadataJson(metadata));
    rev.setRequiredToolsJson(json(metadata.requiredTools()));
    rev.setContentChecksum(checksum);
    rev.setReviewStatus(skill.builtin() ? "not_required" : "pending");
    rev.setCreatedBy(skill.ownerUserId());
    rev.setCreatedAt(now);
    mapper.insertSkillRevision(rev);

    for (AgentSkillResource resource : resources) {
      byte[] content = resource.content().getBytes(StandardCharsets.UTF_8);
      SkillResourceEntity res = new SkillResourceEntity();
      res.setTenantId(skill.tenantId());
      res.setSkillId(skill.id());
      res.setSkillRevision(nextRevision);
      res.setResourcePath(resource.path());
      res.setMediaType("text/plain; charset=utf-8");
      res.setContent(resource.content());
      res.setSizeBytes((long) content.length);
      res.setChecksum(sha256(content));
      mapper.insertSkillResource(res);
    }

    syncCompatibilityResources(skill.tenantId(), skill.id(), resources, now);
    return findById(skill.tenantId(), skill.ownerUserId(), skill.id(), !published).orElseThrow();
  }

  @Override
  public List<AgentSkillResource> findResources(
      String tenantId, String skillId, long skillRevision) {
    return mapper.findResources(tenantId, skillId, skillRevision).stream()
        .map(SkillResourceEntity::toDomain)
        .toList();
  }

  @Override
  public void publish(String tenantId, String userId, String id) {
    int updated = mapper.publish(tenantId, userId, id, Instant.now());
    if (updated == 0) {
      throw new NotFoundException("AgentSkillDraft", id);
    }
  }

  @Override
  public void delete(String tenantId, String userId, String id) {
    int updated = mapper.delete(tenantId, userId, id, Instant.now());
    if (updated == 0) {
      throw new NotFoundException("AgentSkill", id);
    }
  }

  private void syncCompatibilityResources(
      String tenantId, String skillId, List<AgentSkillResource> resources, Instant now) {
    mapper.deleteCompatResources(tenantId, skillId);
    for (AgentSkillResource resource : resources) {
      AgentSkillResourceEntity res = new AgentSkillResourceEntity();
      res.setTenantId(tenantId);
      res.setSkillId(skillId);
      res.setResourcePath(resource.path());
      res.setContent(resource.content());
      res.setCreatedAt(now);
      res.setUpdatedAt(now);
      mapper.insertCompatResource(res);
    }
  }

  private boolean ownerMatches(AgentSkillEntity root, String ownerUserId) {
    return ownerUserId.equals(root.getOwnerUserId());
  }

  private String metadataJson(SkillMetadataParser.ParsedSkillMetadata metadata) {
    return json(
        Map.of(
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
}
