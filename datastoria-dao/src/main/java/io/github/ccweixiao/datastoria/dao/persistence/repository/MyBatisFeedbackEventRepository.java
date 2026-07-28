package io.github.ccweixiao.datastoria.dao.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.github.ccweixiao.datastoria.common.domain.FeedbackEvent;
import io.github.ccweixiao.datastoria.dao.persistence.entity.FeedbackEventEntity;
import io.github.ccweixiao.datastoria.dao.persistence.mapper.FeedbackEventMapper;
import io.github.ccweixiao.datastoria.dao.repository.FeedbackEventRepository;

/** MyBatis-Plus adapter for {@code ds_feedback_event}. */
@Repository
public class MyBatisFeedbackEventRepository implements FeedbackEventRepository {

  private final FeedbackEventMapper mapper;

  public MyBatisFeedbackEventRepository(FeedbackEventMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public FeedbackEvent upsert(FeedbackEvent e) {
    Instant now = Instant.now();
    Optional<FeedbackEventEntity> existing = findEntity(e);
    FeedbackEventEntity row;
    if (existing.isEmpty()) {
      row = FeedbackEventEntity.fromDomain(e);
      row.setCreatedAt(now);
      row.setUpdatedAt(now);
      mapper.insertFeedback(row);
    } else {
      // Update keyed by the existing row's id (not the incoming id).
      FeedbackEventEntity toUpdate = FeedbackEventEntity.fromDomain(e);
      toUpdate.setId(existing.get().getId());
      mapper.updateFeedback(toUpdate, now);
      row = findEntity(e).orElseThrow();
    }
    return row.toDomain();
  }

  @Override
  public Optional<FeedbackEvent> find(
      String tenantId, String userId, String source, String sessionId, String messageId) {
    return findEntity(
            new FeedbackEvent(
                null, tenantId, userId, source, sessionId, messageId, false, null, null, null,
                false, null, null))
        .map(FeedbackEventEntity::toDomain);
  }

  private Optional<FeedbackEventEntity> findEntity(FeedbackEvent key) {
    FeedbackEventEntity e =
        mapper.selectOne(
            new LambdaQueryWrapper<FeedbackEventEntity>()
                .eq(FeedbackEventEntity::getTenantId, key.tenantId())
                .eq(FeedbackEventEntity::getUserId, key.userId())
                .eq(FeedbackEventEntity::getSource, key.source())
                .eq(FeedbackEventEntity::getSessionId, key.sessionId())
                .eq(FeedbackEventEntity::getMessageId, key.messageId()));
    return Optional.ofNullable(e);
  }

  @Override
  public List<FeedbackEvent> findForReport(String tenantId, String source, Instant createdAfter) {
    return mapper.findForReport(tenantId, source, createdAfter).stream()
        .map(FeedbackEventEntity::toDomain)
        .toList();
  }
}
