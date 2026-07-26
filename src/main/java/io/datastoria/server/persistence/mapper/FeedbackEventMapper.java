package io.datastoria.server.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.FeedbackEventEntity;

/** Mapper for {@code ds_feedback_event}; the upsert lives in the adapter. */
public interface FeedbackEventMapper extends BaseMapper<FeedbackEventEntity> {

  int insertFeedback(FeedbackEventEntity entity);

  int updateFeedback(@Param("entity") FeedbackEventEntity entity, @Param("now") Instant now);

  List<FeedbackEventEntity> findForReport(
      @Param("tenantId") String tenantId,
      @Param("source") String source,
      @Param("createdAfter") Instant createdAfter);
}
