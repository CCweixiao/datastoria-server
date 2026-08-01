package io.github.ccweixiao.datastoria.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.config.SecurityProperties;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.common.dto.CreateUserRequest;
import io.github.ccweixiao.datastoria.common.dto.UpdateUserRequest;
import io.github.ccweixiao.datastoria.common.error.ConflictException;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Admin user-account management: create/list/update/disable/reset-password. */
@Service
public class UserAccountService {

  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final SecurityProperties properties;
  private final Scheduler jdbcScheduler;

  public UserAccountService(
      UserAccountRepository users,
      PasswordEncoder passwordEncoder,
      SecurityProperties properties,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.properties = properties;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<UserAccount> create(CreateUserRequest req) {
    return Mono.fromCallable(() -> doCreate(req)).subscribeOn(jdbcScheduler);
  }

  public Mono<List<UserAccount>> findAll(String tenantId) {
    return Mono.fromCallable(() -> users.findAll(tenantId)).subscribeOn(jdbcScheduler);
  }

  public Mono<UserAccount> findByUserId(String userId) {
    return Mono.fromCallable(
            () ->
                users
                    .findByUserId(userId)
                    .orElseThrow(() -> new NotFoundException("UserAccount", userId)))
        .subscribeOn(jdbcScheduler);
  }

  public Mono<UserAccount> update(String userId, UpdateUserRequest req) {
    return Mono.fromCallable(() -> doUpdate(userId, req)).subscribeOn(jdbcScheduler);
  }

  public Mono<UserAccount> resetPassword(String userId, String newPassword) {
    return Mono.fromCallable(
            () -> {
              UserAccount account = load(userId);
              return users.save(
                  new UserAccount(
                      account.userId(),
                      account.tenantId(),
                      account.username(),
                      account.email(),
                      passwordEncoder.encode(newPassword),
                      account.role(),
                      account.status(),
                      account.createdAt(),
                      Instant.now()));
            })
        .subscribeOn(jdbcScheduler);
  }

  private UserAccount doCreate(CreateUserRequest req) {
    if (users.existsByUsername(req.username())) {
      throw new ConflictException("Username already exists: " + req.username());
    }
    Instant now = Instant.now();
    String role = req.role() != null ? req.role() : UserAccount.ROLE_USER;
    UserAccount account =
        new UserAccount(
            Ulid.next(),
            properties.getDefaultTenant(),
            req.username(),
            req.email(),
            passwordEncoder.encode(req.password()),
            role,
            1,
            now,
            now);
    return users.save(account);
  }

  private UserAccount doUpdate(String userId, UpdateUserRequest req) {
    UserAccount account = load(userId);
    String role = req.role() != null ? req.role() : account.role();
    Integer status = req.status() != null ? Integer.valueOf(req.status()) : account.status();
    String email = req.email() != null ? req.email() : account.email();
    return users.update(
        new UserAccount(
            account.userId(),
            account.tenantId(),
            account.username(),
            email,
            account.passwordHash(),
            role,
            status,
            account.createdAt(),
            Instant.now()));
  }

  private UserAccount load(String userId) {
    return users
        .findByUserId(userId)
        .orElseThrow(() -> new NotFoundException("UserAccount", userId));
  }
}
