package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.ccweixiao.datastoria.common.domain.ChatSession;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.dao.persistence.entity.ChatSessionEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.ChatSessionMapper;
import io.github.ccweixiao.datastoria.dao.repository.ChatSessionRepository;
import io.github.ccweixiao.datastoria.dao.repository.SessionListCursor;
import io.github.ccweixiao.datastoria.dao.repository.SessionPage;

/** MyBatis-Plus adapter for {@code ds_chat_session}, including opaque-cursor keyset pagination. */
@Repository
public class MyBatisChatSessionRepository implements ChatSessionRepository {

  private final ChatSessionMapper mapper;

  public MyBatisChatSessionRepository(ChatSessionMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ChatSession save(ChatSession s) {
    Instant now = Instant.now();
    ChatSessionEntity e = ChatSessionEntity.fromDomain(s);
    e.setRevision(0L);
    e.setCreatedAt(now);
    e.setUpdatedAt(now);
    mapper.insertSession(e);
    return findById(s.id(), s.tenantId(), s.userId())
        .orElseThrow(() -> new NotFoundException("ChatSession", s.id()));
  }

  @Override
  public Optional<ChatSession> findById(String id, String tenantId, String userId) {
    ChatSessionEntity e =
        mapper.selectOne(
            new LambdaQueryWrapper<ChatSessionEntity>()
                .eq(ChatSessionEntity::getId, id)
                .eq(ChatSessionEntity::getTenantId, tenantId)
                .eq(ChatSessionEntity::getUserId, userId));
    return Optional.ofNullable(e).map(ChatSessionEntity::toDomain);
  }

  @Override
  public SessionPage findPage(
      String tenantId, String userId, String connectionId, SessionListCursor cursor, int limit) {
    Instant cursorUpdatedAt = cursor != null ? cursor.updatedAt() : null;
    String cursorId = cursor != null ? cursor.sessionId() : null;
    List<ChatSessionEntity> rows =
        mapper.findPage(tenantId, userId, connectionId, cursorUpdatedAt, cursorId, limit + 1);
    if (rows.size() <= limit) {
      return new SessionPage(rows.stream().map(ChatSessionEntity::toDomain).toList(), null);
    }
    List<ChatSession> page =
        rows.subList(0, limit).stream().map(ChatSessionEntity::toDomain).toList();
    ChatSession last = page.get(page.size() - 1);
    String nextCursor = SessionListCursor.encode(last.updatedAt(), last.id());
    return new SessionPage(page, nextCursor);
  }

  @Override
  public ChatSession rename(String id, String tenantId, String userId, String title) {
    int affected = mapper.rename(id, tenantId, userId, title, Instant.now());
    if (affected == 0) {
      throw new NotFoundException("ChatSession", id);
    }
    return findById(id, tenantId, userId).orElseThrow();
  }

  @Override
  public void delete(String id, String tenantId, String userId) {
    int affected = mapper.delete(id, tenantId, userId);
    if (affected == 0) {
      throw new NotFoundException("ChatSession", id);
    }
  }

  @Override
  public List<ChatSession> findAllByConnection(
      String tenantId, String userId, String connectionId) {
    return mapper.findAllByConnection(tenantId, userId, connectionId).stream()
        .map(ChatSessionEntity::toDomain)
        .toList();
  }
}
