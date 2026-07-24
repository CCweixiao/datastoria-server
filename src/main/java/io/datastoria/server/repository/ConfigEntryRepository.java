package io.datastoria.server.repository;

import java.util.List;
import java.util.Optional;

import io.datastoria.server.domain.ConfigEntry;

/** Repository for layered configuration entries (system &lt; tenant &lt; user). */
public interface ConfigEntryRepository {

  ConfigEntry save(ConfigEntry entry);

  /**
   * Upserts a user-scope entry. If an entry exists for (tenantId, userId, configKey) it is updated
   * with optimistic-lock checks; otherwise a new row is inserted.
   */
  ConfigEntry upsertUserEntry(
      String tenantId, String userId, String configKey, String valueJson, Long ifMatch);

  Optional<ConfigEntry> findById(String id, String tenantId);

  /** Returns all non-deleted entries contributing to the effective config for this user. */
  List<ConfigEntry> findEffective(String tenantId, String userId);

  void softDelete(String id, String tenantId, long expectedRevision);
}
