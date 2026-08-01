package io.github.ccweixiao.datastoria.controller.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.dto.CreateUserRequest;
import io.github.ccweixiao.datastoria.common.dto.ResetPasswordRequest;
import io.github.ccweixiao.datastoria.common.dto.UpdateUserRequest;
import io.github.ccweixiao.datastoria.common.dto.UserResponse;
import io.github.ccweixiao.datastoria.common.identity.AdminAccess;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.UserAccountService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/** Administrator-only management of ordinary user accounts. */
@RestController
@RequestMapping("/api/admin/users")
@AdminAccess
public class UserAdminController {

  private final UserAccountService userAccountService;

  public UserAdminController(UserAccountService userAccountService) {
    this.userAccountService = userAccountService;
  }

  @GetMapping
  public Mono<ResponseEntity<List<UserResponse>>> list() {
    return IdentityContext.current()
        .flatMap(identity -> userAccountService.findAll(identity.tenantId()))
        .map(list -> list.stream().map(UserResponse::from).toList())
        .map(ResponseEntity::ok);
  }

  @PostMapping
  public Mono<ResponseEntity<UserResponse>> create(@RequestBody @Valid CreateUserRequest req) {
    return IdentityContext.current()
        .flatMap(identity -> userAccountService.create(identity.tenantId(), req))
        .map(UserResponse::from)
        .map(ResponseEntity::ok);
  }

  @GetMapping("/{userId}")
  public Mono<ResponseEntity<UserResponse>> get(@PathVariable String userId) {
    return IdentityContext.current()
        .flatMap(identity -> userAccountService.findByUserId(identity.tenantId(), userId))
        .map(UserResponse::from)
        .map(ResponseEntity::ok);
  }

  @PutMapping("/{userId}")
  public Mono<ResponseEntity<UserResponse>> update(
      @PathVariable String userId, @RequestBody @Valid UpdateUserRequest req) {
    return IdentityContext.current()
        .flatMap(identity -> userAccountService.update(identity.tenantId(), userId, req))
        .map(UserResponse::from)
        .map(ResponseEntity::ok);
  }

  @PostMapping("/{userId}/reset-password")
  public Mono<ResponseEntity<UserResponse>> resetPassword(
      @PathVariable String userId, @RequestBody @Valid ResetPasswordRequest req) {
    return IdentityContext.current()
        .flatMap(
            identity ->
                userAccountService.resetPassword(identity.tenantId(), userId, req.password()))
        .map(UserResponse::from)
        .map(ResponseEntity::ok);
  }

  @DeleteMapping("/{userId}")
  public Mono<ResponseEntity<Void>> delete(@PathVariable String userId) {
    return IdentityContext.current()
        .flatMap(identity -> userAccountService.delete(identity.tenantId(), userId))
        .thenReturn(ResponseEntity.noContent().build());
  }
}
