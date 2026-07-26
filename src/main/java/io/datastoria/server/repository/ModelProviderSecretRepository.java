package io.datastoria.server.repository;

/**
 * Privileged write used by credential rotation to (un)link a secret from a provider without bumping
 * {@code revision}. Kept as a dedicated port so {@code ProviderService} does not depend on a
 * concrete repository implementation; the existing {@link ModelProviderRepository} contract is
 * unchanged.
 */
public interface ModelProviderSecretRepository {

  /**
   * Links ({@code secretId != null}) or unlinks ({@code secretId == null}) the provider's secret.
   */
  void updateSecretId(String id, String tenantId, String secretId);
}
