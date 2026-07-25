package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.UserState;

public interface UserStateRepository {
  List<UserState> findAll(String tenantId, String userId, String namespace);

  Optional<UserState> find(String tenantId, String userId, String namespace, String key);

  UserState upsert(UserState state, Long expectedRevision);

  void delete(String tenantId, String userId, String namespace, String key);
}
