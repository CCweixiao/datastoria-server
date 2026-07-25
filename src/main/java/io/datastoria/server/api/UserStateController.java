package io.datastoria.server.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.datastoria.server.dto.UserStateRequest;
import io.datastoria.server.dto.UserStateResponse;
import io.datastoria.server.identity.IdentityContext;
import io.datastoria.server.service.UserStateService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/me/state")
public class UserStateController {

  private final UserStateService service;

  public UserStateController(UserStateService service) {
    this.service = service;
  }

  @GetMapping("/{namespace}")
  public Mono<ResponseEntity<List<UserStateResponse>>> list(
      @PathVariable @Pattern(regexp = "[a-z][a-z0-9._-]{0,127}") String namespace) {
    return IdentityContext.current()
        .flatMap(identity -> service.list(namespace, identity))
        .map(ResponseEntity::ok);
  }

  @PutMapping("/{namespace}/{key}")
  public Mono<ResponseEntity<UserStateResponse>> put(
      @PathVariable @Pattern(regexp = "[a-z][a-z0-9._-]{0,127}") String namespace,
      @PathVariable @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,254}") String key,
      @RequestHeader(value = "If-Match", required = false) String ifMatchHeader,
      @RequestBody @Valid UserStateRequest request) {
    Long expected = RevisionHeader.parse(ifMatchHeader);
    return IdentityContext.current()
        .flatMap(identity -> service.put(namespace, key, request, expected, identity))
        .map(this::etag);
  }

  @DeleteMapping("/{namespace}/{key}")
  public Mono<ResponseEntity<Void>> delete(
      @PathVariable @Pattern(regexp = "[a-z][a-z0-9._-]{0,127}") String namespace,
      @PathVariable @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,254}") String key) {
    return IdentityContext.current()
        .flatMap(identity -> service.delete(namespace, key, identity))
        .thenReturn(ResponseEntity.noContent().build());
  }

  private ResponseEntity<UserStateResponse> etag(UserStateResponse body) {
    return ResponseEntity.ok().eTag(String.valueOf(body.revision())).body(body);
  }
}
