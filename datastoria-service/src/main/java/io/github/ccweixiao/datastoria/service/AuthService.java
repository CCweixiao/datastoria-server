package io.github.ccweixiao.datastoria.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.dto.LoginResponse;
import io.github.ccweixiao.datastoria.common.dto.UserResponse;
import io.github.ccweixiao.datastoria.common.error.BadCredentialsException;
import io.github.ccweixiao.datastoria.common.identity.JwtTokenService;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Username+password login. BCrypt verification is blocking, so the lookup/match runs on the JDBC
 * scheduler. Invalid username, wrong password, or a disabled account all yield {@link
 * BadCredentialsException} (→ 401) to avoid leaking which field failed.
 */
@Service
public class AuthService {

  private final UserAccountRepository users;
  private final JwtTokenService tokenService;
  private final PasswordEncoder passwordEncoder;
  private final Scheduler jdbcScheduler;

  public AuthService(
      UserAccountRepository users,
      JwtTokenService tokenService,
      PasswordEncoder passwordEncoder,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.users = users;
    this.tokenService = tokenService;
    this.passwordEncoder = passwordEncoder;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<LoginResponse> login(String username, String password) {
    return Mono.fromCallable(() -> authenticate(username, password))
        .subscribeOn(jdbcScheduler)
        .map(
            account -> {
              String token =
                  tokenService.sign(
                      account.userId(),
                      account.tenantId(),
                      account.role(),
                      account.username(),
                      account.tokenVersion());
              return new LoginResponse(token, UserResponse.from(account));
            });
  }

  private io.github.ccweixiao.datastoria.common.domain.UserAccount authenticate(
      String username, String password) {
    io.github.ccweixiao.datastoria.common.domain.UserAccount account =
        users
            .findByUsername(username)
            .orElseThrow(BadCredentialsException::new);
    if (!account.enabled()) {
      throw new BadCredentialsException();
    }
    if (account.passwordHash() == null
        || !passwordEncoder.matches(password, account.passwordHash())) {
      throw new BadCredentialsException();
    }
    return account;
  }
}
