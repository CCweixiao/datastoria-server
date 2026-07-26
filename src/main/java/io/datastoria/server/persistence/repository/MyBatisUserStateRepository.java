package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.api.error.RevisionConflictException;
import io.datastoria.server.domain.UserState;
import io.datastoria.server.persistence.entity.UserStateEntity;
import io.datastoria.server.persistence.mapper.UserStateMapper;
import io.datastoria.server.repository.UserStateRepository;

/**
 * MyBatis-Plus adapter for {@code ds_user_state}. The upsert is revision-guarded when an expected
 * revision is supplied; the last-write-wins path retries the UPDATE if a concurrent INSERT wins the
 * race (catching {@code DataIntegrityViolationException}).
 */
@Repository
public class MyBatisUserStateRepository implements UserStateRepository {

  private final UserStateMapper mapper;

  public MyBatisUserStateRepository(UserStateMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<UserState> findAll(String tenantId, String userId, String namespace) {
    return mapper.findAll(tenantId, userId, namespace).stream()
        .map(UserStateEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<UserState> find(String tenantId, String userId, String namespace, String key) {
    return Optional.ofNullable(mapper.find(tenantId, userId, namespace, key))
        .map(UserStateEntity::toDomain);
  }

  @Override
  public UserState upsert(UserState state, Long expectedRevision) {
    if (expectedRevision == null) {
      return upsertLastWriteWins(state);
    }
    UserStateEntity existing =
        mapper.find(state.tenantId(), state.userId(), state.namespace(), state.key());
    if (existing == null) {
      if (expectedRevision != 0) {
        throw new RevisionConflictException("UserState", state.key(), expectedRevision, 0);
      }
      mapper.insert(UserStateEntity.fromDomain(state), Instant.now());
    } else {
      int rows =
          mapper.casUpdate(UserStateEntity.fromDomain(state), expectedRevision, Instant.now());
      if (rows == 0) {
        throw new RevisionConflictException(
            "UserState", state.key(), expectedRevision, existing.toDomain().revision());
      }
    }
    return find(state.tenantId(), state.userId(), state.namespace(), state.key()).orElseThrow();
  }

  private UserState upsertLastWriteWins(UserState state) {
    UserStateEntity e = UserStateEntity.fromDomain(state);
    if (mapper.updateNoRevisionCheck(e, Instant.now()) == 1) {
      return find(state.tenantId(), state.userId(), state.namespace(), state.key()).orElseThrow();
    }
    try {
      mapper.insert(e, Instant.now());
    } catch (DataIntegrityViolationException race) {
      if (mapper.updateNoRevisionCheck(e, Instant.now()) == 1) {
        return find(state.tenantId(), state.userId(), state.namespace(), state.key()).orElseThrow();
      }
      throw race;
    }
    return find(state.tenantId(), state.userId(), state.namespace(), state.key()).orElseThrow();
  }

  @Override
  public void delete(String tenantId, String userId, String namespace, String key) {
    int rows = mapper.delete(tenantId, userId, namespace, key);
    if (rows == 0) {
      throw new NotFoundException("UserState", key);
    }
  }
}
