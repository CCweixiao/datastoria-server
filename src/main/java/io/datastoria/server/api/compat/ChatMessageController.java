package io.datastoria.server.api.compat;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.datastoria.server.dto.ChatMessageDTO;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.MessageService;
import reactor.core.publisher.Mono;

/**
 * A08 — replay session messages. Owner or share visitor (read). Returns an array ordered by
 * {@code sequence ASC}; empty array when the session has no messages.
 */
@RestController
@RequestMapping("/api/ai/chat/sessions/{sessionId}/messages")
public class ChatMessageController {

  private final MessageService messageService;

  public ChatMessageController(MessageService messageService) {
    this.messageService = messageService;
  }

  @GetMapping
  public Mono<ResponseEntity<List<ChatMessageDTO>>> list(
      @PathVariable String sessionId,
      @RequestHeader(value = "X-Session-Share-Code", required = false) String shareCode) {
    return IdentityContext.current()
        .flatMap(identity -> messageService.listMessages(sessionId, identity, shareCode))
        .map(ResponseEntity::ok);
  }
}
