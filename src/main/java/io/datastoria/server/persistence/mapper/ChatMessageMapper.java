package io.datastoria.server.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.datastoria.server.persistence.entity.ChatMessageEntity;

/**
 * Mapper for {@code ds_chat_message}. The upsert orchestration (lookup-then-insert/update with
 * concurrent-insert retry) lives in the adapter; this mapper exposes the primitive operations.
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

  int insertMessage(ChatMessageEntity entity);

  int updateMessage(ChatMessageEntity entity);
}
