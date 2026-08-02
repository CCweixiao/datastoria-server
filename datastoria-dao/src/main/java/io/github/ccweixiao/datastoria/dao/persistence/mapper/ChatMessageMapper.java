package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.ChatMessageEntity;

/**
 * Mapper for {@code ds_chat_message}. The upsert orchestration (lookup-then-insert/update with
 * concurrent-insert retry) lives in the adapter; this mapper exposes the primitive operations.
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

  int insertMessage(ChatMessageEntity entity);

  int updateMessage(ChatMessageEntity entity);

  /** {@code COALESCE(MAX(sequence), 0)} for a session — the next free sequence slot. */
  Long maxSequence(@Param("tenantId") String tenantId, @Param("sessionId") String sessionId);
}
