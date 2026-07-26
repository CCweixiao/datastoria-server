package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import io.datastoria.server.domain.SessionShare;
import io.datastoria.server.persistence.entity.SessionShareEntity;
import io.datastoria.server.persistence.mapper.SessionShareMapper;
import io.datastoria.server.repository.SessionShareRepository;

/** MyBatis-Plus adapter for {@code ds_session_share}. */
@Repository
public class MyBatisSessionShareRepository implements SessionShareRepository {

  private final SessionShareMapper mapper;

  public MyBatisSessionShareRepository(SessionShareMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public SessionShare issue(SessionShare s) {
    Instant now = Instant.now();
    SessionShareEntity e = SessionShareEntity.fromDomain(s);
    e.setCreatedAt(now);
    mapper.insertShare(e);
    return findByTokenHash(s.tokenHash())
        .orElseThrow(() -> new IllegalStateException("issued share row not found"));
  }

  @Override
  public Optional<SessionShare> findActive(String sessionId, String tenantId) {
    return Optional.ofNullable(mapper.findActive(tenantId, sessionId))
        .map(SessionShareEntity::toDomain);
  }

  @Override
  public Optional<SessionShare> findByTokenHash(String tokenHash) {
    return Optional.ofNullable(mapper.findByTokenHash(tokenHash)).map(SessionShareEntity::toDomain);
  }

  @Override
  public int revoke(String sessionId, String tenantId) {
    return mapper.revoke(tenantId, sessionId, Instant.now());
  }
}
