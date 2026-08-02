package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.ccweixiao.datastoria.common.domain.ChatMessage;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.ChatMessageEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.ChatMessageMapper;
import io.github.ccweixiao.datastoria.dao.repository.ChatMessageRepository;

/**
 * MyBatis-Plus adapter for {@code ds_chat_message}.
 *
 * <p>{@link #save} uses a manual lookup-then-upsert and catches {@link RuntimeException} (not
 * {@code DuplicateKeyException}) on the insert path because exception translation can vary by
 * execution path. On a concurrent insert of the same id the row is re-read and updated; a genuine
 * failure is rethrown.
 *
 * <p>{@link #saveInitialMessages} is the idempotent batch path used on A04 create: it plans the
 * batch from a single lean read (existing ids + current max sequence), skips ids already present,
 * and appends the rest past the current max. Inserts are guarded against a {@code
 * uk_message_session_sequence} collision by recomputing the max and retrying, so a concurrent
 * append never surfaces as a duplicate-key error.
 */
@Repository
public class MyBatisChatMessageRepository implements ChatMessageRepository {

  private final ChatMessageMapper mapper;

  /** Bounded retries when a concurrent append collides on the session-sequence unique key. */
  private static final int SEQUENCE_COLLISION_RETRIES = 5;

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
  public void saveInitialMessages(
      String tenantId,
      String sessionId,
      String userId,
      List<ChatMessageRepository.InitialMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    // One lean read plans the whole batch: which ids already exist and the next free sequence.
    Set<String> existingIds = new HashSet<>();
    long nextSeq = 0L;
    for (ChatMessageEntity row : leanSequenceRows(tenantId, sessionId)) {
      if (row.getId() != null) {
        existingIds.add(row.getId());
      }
      if (row.getSequence() != null && row.getSequence() > nextSeq) {
        nextSeq = row.getSequence();
      }
    }
    Instant now = Instant.now();
    for (ChatMessageRepository.InitialMessage m : messages) {
      if (m.id() == null || existingIds.contains(m.id())) {
        continue; // idempotent: already persisted for this session — leave its sequence untouched
      }
      ChatMessageEntity e = new ChatMessageEntity();
      e.setId(m.id());
      e.setTenantId(tenantId);
      e.setSessionId(sessionId);
      e.setUserId(userId);
      e.setRole(m.role());
      e.setPartsJson(m.partsJson());
      e.setMetadataJson(m.metadataJson());
      e.setSequence(++nextSeq);
      e.setCreatedAt(now);
      e.setUpdatedAt(now);
      insertWithSequenceRecovery(e, tenantId, sessionId);
      // Adopt any sequence the recovery path chose so the following message continues past it.
      nextSeq = Math.max(nextSeq, e.getSequence());
      existingIds.add(m.id());
    }
  }

  /** Reads only {@code id, sequence} for a session — never the heavy {@code parts_json}. */
  private List<ChatMessageEntity> leanSequenceRows(String tenantId, String sessionId) {
    return mapper.selectList(
        new LambdaQueryWrapper<ChatMessageEntity>()
            .select(ChatMessageEntity::getId, ChatMessageEntity::getSequence)
            .eq(ChatMessageEntity::getTenantId, tenantId)
            .eq(ChatMessageEntity::getSessionId, sessionId));
  }

  /**
   * Inserts a brand-new message row. A collision on {@code uk_message_session_sequence} (a
   * concurrent append that took the same slot) is recovered by recomputing the session max and
   * retrying; a concurrent insert of the same id is treated as success because the row is present.
   */
  private void insertWithSequenceRecovery(ChatMessageEntity e, String tenantId, String sessionId) {
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt <= SEQUENCE_COLLISION_RETRIES; attempt++) {
      try {
        mapper.insertMessage(e);
        return;
      } catch (RuntimeException failure) {
        // Same-id race: another writer already persisted this exact id — nothing left to do.
        if (findByIdEntity(e.getId(), tenantId, sessionId).isPresent()) {
          return;
        }
        if (!isUniqueConstraintViolation(failure)) {
          throw failure;
        }
        lastFailure = failure;
        Long max = mapper.maxSequence(tenantId, sessionId);
        e.setSequence((max == null ? 0L : max) + 1L);
      }
    }
    throw lastFailure;
  }

  /** True when the failure is a unique-key violation, checked portably across translation paths. */
  private static boolean isUniqueConstraintViolation(Throwable failure) {
    Throwable c = failure;
    while (c != null) {
      if (c instanceof DuplicateKeyException
          || c instanceof SQLIntegrityConstraintViolationException) {
        return true;
      }
      String message = c.getMessage();
      if (message != null) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("duplicate entry") || lower.contains("unique constraint")) {
          return true;
        }
      }
      c = c.getCause();
    }
    return false;
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
