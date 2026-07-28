package io.github.ccweixiao.datastoria.dao.repository;

import java.util.Optional;

import io.github.ccweixiao.datastoria.common.domain.Secret;

/**
 * Persists encrypted secrets. The plaintext value is never accepted or returned; only the AES-GCM
 * envelope fields and a masked hint.
 */
public interface SecretRepository {

  Secret save(Secret secret);

  /** Returns the full encrypted envelope (for decrypt). Never exposes plaintext. */
  Optional<Secret> findEncryptedById(String id, String tenantId);

  /** Returns only the masked fields for UI display. */
  Optional<Secret> findMaskedById(String id, String tenantId);

  void softDelete(String id, String tenantId);
}
