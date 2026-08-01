package io.github.ccweixiao.datastoria.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.ccweixiao.datastoria.common.dto.LoginRequest;
import io.github.ccweixiao.datastoria.common.dto.LoginResponse;
import io.github.ccweixiao.datastoria.common.dto.UserResponse;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;
import io.github.ccweixiao.datastoria.service.AuthService;
import io.github.ccweixiao.datastoria.service.UserAccountService;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;

/**
 * Authentication endpoints. {@code /api/auth/login} is public (listed in {@code
 * JwtIdentityWebFilter}); {@code /api/auth/me} requires a valid Bearer token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final UserAccountService userAccountService;

  public AuthController(AuthService authService, UserAccountService userAccountService) {
    this.authService = authService;
    this.userAccountService = userAccountService;
  }

  /** Exchange username+password for a login JWT. */
  @PostMapping("/login")
  public Mono<ResponseEntity<LoginResponse>> login(@RequestBody @Valid LoginRequest req) {
    return authService.login(req.username(), req.password()).map(ResponseEntity::ok);
  }

  /** Return the account backing the current Bearer token. */
  @GetMapping("/me")
  public Mono<ResponseEntity<UserResponse>> me() {
    return IdentityContext.current()
        .flatMap(
            identity ->
                userAccountService.findByUserId(identity.tenantId(), identity.userId()))
        .map(UserResponse::from)
        .map(ResponseEntity::ok);
  }
}
