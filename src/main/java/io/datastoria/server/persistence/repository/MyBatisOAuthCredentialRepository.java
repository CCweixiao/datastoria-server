package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.OAuthCredential;
import io.datastoria.server.persistence.entity.OAuthCredentialEntity;
import io.datastoria.server.persistence.mapper.OAuthCredentialMapper;
import io.datastoria.server.repository.OAuthCredentialRepository;

/**
 * MyBatis-Plus adapter for {@code ds_oauth_credential}. A lost CAS race raises Spring's {@link
 * OptimisticLockingFailureException} (preserved from the JDBC implementation); {@code save} returns
 * the caller-supplied value verbatim.
 */
@Repository
public class MyBatisOAuthCredentialRepository implements OAuthCredentialRepository {

  private final OAuthCredentialMapper mapper;

  public MyBatisOAuthCredentialRepository(OAuthCredentialMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<OAuthCredential> findByOwner(String tenantId, String userId, String providerKey) {
    return Optional.ofNullable(mapper.findByOwner(tenantId, userId, providerKey))
        .map(OAuthCredentialEntity::toDomain);
  }

  @Override
  public OAuthCredential save(OAuthCredential value) {
    mapper.insertCredential(OAuthCredentialEntity.fromDomain(value));
    return value;
  }

  @Override
  public OAuthCredential update(OAuthCredential value, long expectedRevision) {
    int changed =
        mapper.updateCas(OAuthCredentialEntity.fromDomain(value), expectedRevision, Instant.now());
    if (changed != 1) {
      throw new OptimisticLockingFailureException("OAuth credential revision conflict");
    }
    return findByOwner(value.tenantId(), value.userId(), value.providerKey()).orElseThrow();
  }
}
