package io.datastoria.server.api.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.datastoria.server.api.RevisionHeader;
import io.datastoria.server.dto.UpdateUserPreferenceRequest;
import io.datastoria.server.dto.UserPreferenceResponse;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.UserPreferenceService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/** User-scoped configuration endpoints under /api/me/ai/preferences. */
@RestController
@RequestMapping("/api/me/ai/preferences")
public class UserPreferenceController {

  private final UserPreferenceService service;

  public UserPreferenceController(UserPreferenceService service) {
    this.service = service;
  }

  @GetMapping
  public Mono<ResponseEntity<UserPreferenceResponse>> getPreferences() {
    return IdentityContext.current().flatMap(service::getEffectiveConfig).map(this::withEtag);
  }

  @PutMapping
  public Mono<ResponseEntity<UserPreferenceResponse>> putPreference(
      @RequestBody @Valid UpdateUserPreferenceRequest req,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.upsertUserEntry(req, ifMatch, identity))
        .map(this::withEtag);
  }

  private ResponseEntity<UserPreferenceResponse> withEtag(UserPreferenceResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
