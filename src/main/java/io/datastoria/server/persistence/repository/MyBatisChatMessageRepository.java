package io.datastoria.server.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.domain.ChatMessage;
import io.datastoria.server.persistence.entity.ChatMessageEntity;
import io.datastoria.server.persistence.mapper.ChatMessageMapper;
import io.datastoria.server.repository.ChatMessageRepository;

/**
 * MyBatis-Plus adapter for {@code ds_chat_message}.
 *
 * <p>Uses a manual lookup-then-upsert and catches {@link RuntimeException} (not {@code
 * DuplicateKeyException}) on the insert path: Xerial SQLite does not reliably map {@code
 * SQLITE_CONSTRAINT} to Spring's {@code DataIntegrityViolationException}. On a concurrent insert of
 * the same id the row is re-read and updated; a genuine failure is rethrown.
 */
@Repository
public class MyBatisChatMessageRepository implements ChatMessageRepository {

  private final ChatMessageMapper mapper;

  public MyBatisChatMessageRepository(ChatMessageMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ChatMessage save(ChatMessage m) {
    Instant now = Instant.now();
    Optional<ChatMessageEntity> existing = findByIdEntity(m.id(), m.tenantId(), m.sessionId());
    if (existing.isEmpty()) {
      ChatMessageEntity e = ChatMessageEntity.fromDomain(m);
      e.setCreatedAt(now);
      e.setUpdatedAt(now);
      try {
        mapper.insertMessage(e);
      } catch (RuntimeException insertFailure) {
        // Concurrent insert of the same id may have won the race.
        if (findByIdEntity(m.id(), m.tenantId(), m.sessionId()).isEmpty()) {
          throw insertFailure;
        }
        updateExisting(m, now);
      }
    } else {
      updateExisting(m, now);
    }
    return findByIdEntity(m.id(), m.tenantId(), m.sessionId())
        .map(ChatMessageEntity::toDomain)
        .orElseThrow(() -> new NotFoundException("ChatMessage", m.id()));
  }

  private void updateExisting(ChatMessage m, Instant now) {
    ChatMessageEntity e = ChatMessageEntity.fromDomain(m);
    e.setUpdatedAt(now);
    mapper.updateMessage(e);
  }

  @Override
  public Optional<ChatMessage> findById(String id, String tenantId, String sessionId) {
    return findByIdEntity(id, tenantId, sessionId).map(ChatMessageEntity::toDomain);
  }

  private Optional<ChatMessageEntity> findByIdEntity(String id, String tenantId, String sessionId) {
    ChatMessageEntity e =
        mapper.selectOne(
            new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getId, id)
                .eq(ChatMessageEntity::getTenantId, tenantId)
                .eq(ChatMessageEntity::getSessionId, sessionId));
    return Optional.ofNullable(e);
  }

  @Override
  public List<ChatMessage> findBySession(String sessionId, String tenantId) {
    return mapper
        .selectList(
            new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getTenantId, tenantId)
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .orderByAsc(ChatMessageEntity::getSequence)
                .orderByAsc(ChatMessageEntity::getId))
        .stream()
        .map(ChatMessageEntity::toDomain)
        .toList();
  }

  @Override
  public boolean exists(String tenantId, String userId, String sessionId, String messageId) {
    Long count =
        mapper.selectCount(
            new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getTenantId, tenantId)
                .eq(ChatMessageEntity::getUserId, userId)
                .eq(ChatMessageEntity::getSessionId, sessionId)
                .eq(ChatMessageEntity::getId, messageId));
    return count != null && count > 0;
  }
}
