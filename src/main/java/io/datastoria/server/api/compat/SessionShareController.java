package io.datastoria.server.api.compat;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.datastoria.server.dto.ShareResponse;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.SessionShareService;
import reactor.core.publisher.Mono;

/**
 * A09 — issue a session share code (owner only). A09b — revoke the active share (owner only).
 * Share codes are NOT accepted on these routes; only the session owner may issue or revoke.
 */
@RestController
@RequestMapping("/api/ai/sessions/{sessionId}")
public class SessionShareController {

  private final SessionShareService shareService;

  public SessionShareController(SessionShareService shareService) {
    this.shareService = shareService;
  }

  @PostMapping("/share")
  public Mono<ResponseEntity<ShareResponse>> issue(@PathVariable String sessionId) {
    return IdentityContext.current()
        .flatMap(identity -> shareService.issue(sessionId, identity))
        .map(ResponseEntity::ok);
  }

  @PostMapping("/share:revoke")
  public Mono<ResponseEntity<Void>> revoke(@PathVariable String sessionId) {
    return IdentityContext.current()
        .flatMap(identity -> shareService.revoke(sessionId, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }
}
