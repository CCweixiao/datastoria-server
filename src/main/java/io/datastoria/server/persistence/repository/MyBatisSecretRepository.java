package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.Secret;
import io.datastoria.server.persistence.entity.SecretEntity;
import io.datastoria.server.persistence.mapper.SecretMapper;
import io.datastoria.server.repository.SecretRepository;

/** MyBatis-Plus adapter for {@code ds_secret}. */
@Repository
public class MyBatisSecretRepository implements SecretRepository {

  private final SecretMapper mapper;

  public MyBatisSecretRepository(SecretMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Secret save(Secret s) {
    Instant now = Instant.now();
    SecretEntity e = SecretEntity.fromDomain(s);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    mapper.insertSecret(e);
    return findMaskedById(s.id(), s.tenantId())
        .orElseThrow(() -> new IllegalStateException("saved secret row not found"));
  }

  @Override
  public Optional<Secret> findEncryptedById(String id, String tenantId) {
    return Optional.ofNullable(mapper.findEncryptedById(id, tenantId)).map(SecretEntity::toDomain);
  }

  @Override
  public Optional<Secret> findMaskedById(String id, String tenantId) {
    return Optional.ofNullable(mapper.findMaskedById(id, tenantId)).map(SecretEntity::toDomain);
  }

  @Override
  public void softDelete(String id, String tenantId) {
    mapper.softDelete(id, tenantId, Instant.now());
  }
}
