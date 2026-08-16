package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.ccweixiao.datastoria.common.domain.ConfigEntry;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.RevisionConflictException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.ConfigEntryEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.ConfigEntryMapper;
import io.github.ccweixiao.datastoria.dao.repository.ConfigEntryRepository;

/**
 * MyBatis-Plus adapter for {@code ds_config_entry}, including the user-scope optimistic-lock
 * upsert.
 */
@Repository
public class MyBatisConfigEntryRepository implements ConfigEntryRepository {

  private final ConfigEntryMapper mapper;

  public MyBatisConfigEntryRepository(ConfigEntryMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ConfigEntry save(ConfigEntry entry) {
    Instant now = Instant.now();
    ConfigEntryEntity e = ConfigEntryEntity.fromDomain(entry);
    if (e.getId() == null) {
      e.setId(Ulid.next());
    }
    if (e.getSchemaVersion() == null) {
      e.setSchemaVersion("1");
    }
    e.setRevision(entry.revision());
    e.setCreatedAt(entry.createdAt() != null ? entry.createdAt() : now);
    e.setUpdatedAt(entry.updatedAt() != null ? entry.updatedAt() : now);
    mapper.insertConfigEntry(e);
    return findById(entry.id() != null ? entry.id() : e.getId(), entry.tenantId())
        .orElseThrow(() -> new NotFoundException("ConfigEntry", e.getId()));
  }

  @Override
  public ConfigEntry upsertUserEntry(
      String tenantId, String userId, String configKey, String valueJson, Long ifMatch) {
    ConfigEntryEntity current = mapper.findUserEntry(tenantId, userId, configKey);
    if (current != null) {
      long expected = ifMatch != null ? ifMatch : current.getRevision();
      int rows = mapper.casUpdate(current.getId(), tenantId, valueJson, expected, Instant.now());
      if (rows == 0) {
        throw new RevisionConflictException(
            "ConfigEntry", current.getId(), expected, current.getRevision());
      }
      return findById(current.getId(), tenantId).orElseThrow();
    }
    ConfigEntry fresh =
        new ConfigEntry(
            null, tenantId, "user", userId, configKey, valueJson, "1", 0, null, null, null);
    return save(fresh);
  }

  @Override
  public Optional<ConfigEntry> findTenantEntry(String tenantId, String configKey) {
    return Optional.ofNullable(mapper.findTenantEntry(tenantId, configKey))
        .map(ConfigEntryEntity::toDomain);
  }

  @Override
  public ConfigEntry upsertTenantEntry(
      String tenantId, String configKey, String valueJson, Long ifMatch) {
    ConfigEntryEntity current = mapper.findTenantEntry(tenantId, configKey);
    if (current != null) {
      long expected = ifMatch != null ? ifMatch : current.getRevision();
      int rows = mapper.casUpdate(current.getId(), tenantId, valueJson, expected, Instant.now());
      if (rows == 0) {
        throw new RevisionConflictException(
            "ConfigEntry", current.getId(), expected, current.getRevision());
      }
      return findById(current.getId(), tenantId).orElseThrow();
    }
    if (ifMatch != null && ifMatch > 0) {
      // The caller saw a revision that no longer exists (or never did); treat as a conflict
      // instead of silently creating a fresh entry.
      throw new RevisionConflictException("ConfigEntry", configKey, ifMatch, 0);
    }
    ConfigEntry fresh =
        new ConfigEntry(
            null, tenantId, "tenant", tenantId, configKey, valueJson, "1", 0, null, null, null);
    return save(fresh);
  }

  @Override
  public Optional<ConfigEntry> findById(String id, String tenantId) {
    return Optional.ofNullable(
            mapper.selectOne(
                new LambdaQueryWrapper<ConfigEntryEntity>()
                    .eq(ConfigEntryEntity::getId, id)
                    .eq(ConfigEntryEntity::getTenantId, tenantId)))
        .map(ConfigEntryEntity::toDomain);
  }

  @Override
  public List<ConfigEntry> findEffective(String tenantId, String userId) {
    return mapper.findEffective(tenantId, userId).stream()
        .map(ConfigEntryEntity::toDomain)
        .toList();
  }

  @Override
  public void softDelete(String id, String tenantId, long expectedRevision) {
    int rows = mapper.softDelete(id, tenantId, expectedRevision, Instant.now());
    if (rows == 0) {
      throw new NotFoundException("ConfigEntry", id);
    }
  }
}
