package io.github.ccweixiao.datastoria.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.ChatMessage;
import io.github.ccweixiao.datastoria.common.dto.ChatMessageDTO;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ChatMessageRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * A08 — replay session messages ordered by {@code sequence ASC}. Owner or share visitor with a
 * valid code may read; see {@link SessionService#resolveAccess} for the access contract.
 */
@Service
public class MessageService {

  private final ChatMessageRepository messageRepo;
  private final SessionService sessionService;
  private final ObjectMapper objectMapper;
  private final Scheduler jdbcScheduler;

  public MessageService(
      ChatMessageRepository messageRepo,
      SessionService sessionService,
      ObjectMapper objectMapper,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.messageRepo = messageRepo;
    this.sessionService = sessionService;
    this.objectMapper = objectMapper;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<List<ChatMessageDTO>> listMessages(
      String sessionId, Identity identity, String shareCode) {
    return Mono.fromCallable(
            () -> {
              // Resolve access first so 404/403 semantics match A05/A06/A07.
              SessionService.SessionAccess access =
                  sessionService.resolveAccess(sessionId, identity, shareCode, false);
              List<ChatMessage> rows =
                  messageRepo.findBySession(access.session().id(), access.session().tenantId());
              return rows.stream().map(this::toDto).toList();
            })
        .subscribeOn(jdbcScheduler);
  }

  private ChatMessageDTO toDto(ChatMessage m) {
    JsonNode parts = readTree(m.partsJson());
    JsonNode metadata = m.metadataJson() == null ? null : readTree(m.metadataJson());
    return ChatMessageDTO.from(m, parts, metadata);
  }

  private JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to deserialise persisted JSON: " + json, e);
    }
  }
}
