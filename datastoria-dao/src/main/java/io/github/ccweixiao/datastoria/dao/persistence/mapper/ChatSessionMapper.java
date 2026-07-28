package io.github.ccweixiao.datastoria.dao.persistence.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import io.github.ccweixiao.datastoria.dao.persistence.entity.ChatSessionEntity;

/**
 * Mapper for {@code ds_chat_session}. {@link #findPage} is the keyset-paginated read ordered by
 * {@code (updated_at DESC, id DESC)}; the caller over-fetches by one to detect the next page.
 */
public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {

  int insertSession(ChatSessionEntity entity);

  List<ChatSessionEntity> findPage(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("connectionId") String connectionId,
      @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
      @Param("cursorId") String cursorId,
      @Param("limit") int limit);

  int rename(
      @Param("id") String id,
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("title") String title,
      @Param("now") Instant now);

  int delete(
      @Param("id") String id, @Param("tenantId") String tenantId, @Param("userId") String userId);

  List<ChatSessionEntity> findAllByConnection(
      @Param("tenantId") String tenantId,
      @Param("userId") String userId,
      @Param("connectionId") String connectionId);
}
