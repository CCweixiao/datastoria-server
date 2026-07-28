package io.github.ccweixiao.datastoria.controller.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.dto.UserModelPreferenceRequest;
import io.github.ccweixiao.datastoria.common.dto.UserModelPreferenceResponse;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.controller.RevisionHeader;
import io.github.ccweixiao.datastoria.service.UserPreferenceService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/** User-scoped model selection endpoint under /api/me/ai/model-preference. */
@RestController
@RequestMapping("/api/me/ai/model-preference")
public class UserModelPreferenceController {

  private final UserPreferenceService service;

  public UserModelPreferenceController(UserPreferenceService service) {
    this.service = service;
  }

  @GetMapping
  public Mono<ResponseEntity<UserModelPreferenceResponse>> getPreference() {
    return IdentityContext.current().flatMap(service::getModelPreference).map(ResponseEntity::ok);
  }

  @PutMapping
  public Mono<ResponseEntity<UserModelPreferenceResponse>> putPreference(
      @RequestBody @Valid UserModelPreferenceRequest req,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
    Long ifMatch = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.setModelPreference(req, ifMatch, identity))
        .map(this::withEtag);
  }

  private ResponseEntity<UserModelPreferenceResponse> withEtag(UserModelPreferenceResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
