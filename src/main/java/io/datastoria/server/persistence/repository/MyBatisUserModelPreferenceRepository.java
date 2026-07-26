package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.Ulid;
import io.datastoria.server.domain.UserModelPreference;
import io.datastoria.server.persistence.entity.UserModelPreferenceEntity;
import io.datastoria.server.persistence.mapper.UserModelPreferenceMapper;
import io.datastoria.server.repository.UserModelPreferenceRepository;

/** MyBatis-Plus adapter for {@code ds_user_model_preference}. */
@Repository
public class MyBatisUserModelPreferenceRepository implements UserModelPreferenceRepository {

  private final UserModelPreferenceMapper mapper;

  public MyBatisUserModelPreferenceRepository(UserModelPreferenceMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public UserModelPreference upsert(
      String tenantId, String userId, String selectedModelId, String preferenceJson, Long ifMatch) {
    UserModelPreferenceEntity current = mapper.findByUser(tenantId, userId);
    if (current != null) {
      long expected = ifMatch != null ? ifMatch : current.getRevision();
      UserModelPreferenceEntity e = new UserModelPreferenceEntity();
      e.setTenantId(tenantId);
      e.setUserId(userId);
      e.setSelectedModelId(selectedModelId);
      e.setPreferenceJson(preferenceJson);
      int rows = mapper.casUpdate(e, expected, Instant.now());
      if (rows == 0) {
        throw new RevisionConflictException(
            "UserModelPreference", current.getId(), expected, current.getRevision());
      }
      return findByUser(tenantId, userId).orElseThrow();
    }
    UserModelPreferenceEntity e = new UserModelPreferenceEntity();
    e.setId(Ulid.next());
    e.setTenantId(tenantId);
    e.setUserId(userId);
    e.setSelectedModelId(selectedModelId);
    e.setPreferenceJson(preferenceJson);
    e.setRevision(0L);
    Instant now = Instant.now();
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    mapper.insert(e);
    return findByUser(tenantId, userId).orElseThrow();
  }

  @Override
  public Optional<UserModelPreference> findByUser(String tenantId, String userId) {
    return Optional.ofNullable(mapper.findByUser(tenantId, userId))
        .map(UserModelPreferenceEntity::toDomain);
  }
}
