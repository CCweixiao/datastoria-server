package io.github.ccweixiao.datastoria.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.common.dto.CreateUserRequest;
import io.github.ccweixiao.datastoria.common.dto.UpdateUserRequest;
import io.github.ccweixiao.datastoria.common.error.ApiErrorCode;
import io.github.ccweixiao.datastoria.common.error.ConflictException;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.error.ProtectedAdminAccountException;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Admin user-account management: create/list/update/disable/reset-password. */
@Service
public class UserAccountService {

  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Scheduler jdbcScheduler;

  public UserAccountService(
      UserAccountRepository users,
      PasswordEncoder passwordEncoder,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jdbcScheduler = jdbcScheduler;
  }

  public Mono<UserAccount> create(String tenantId, CreateUserRequest req) {
    return Mono.fromCallable(() -> doCreate(tenantId, req)).subscribeOn(jdbcScheduler);
  }

  public Mono<List<UserAccount>> findAll(String tenantId) {
    return Mono.fromCallable(
            () -> users.findAll(tenantId).stream().filter(account -> !account.isAdmin()).toList())
        .subscribeOn(jdbcScheduler);
  }

  public Mono<UserAccount> findByUserId(String tenantId, String userId) {
    return Mono.fromCallable(() -> requireOrdinaryUser(tenantId, userId))
        .subscribeOn(jdbcScheduler);
  }

  /** Loads the authenticated user's own account, including the protected administrator account. */
  public Mono<UserAccount> findCurrentAccount(String tenantId, String userId) {
    return Mono.fromCallable(() -> load(tenantId, userId)).subscribeOn(jdbcScheduler);
  }

  public Mono<UserAccount> update(String tenantId, String userId, UpdateUserRequest req) {
    return Mono.fromCallable(() -> doUpdate(tenantId, userId, req)).subscribeOn(jdbcScheduler);
  }

  public Mono<UserAccount> resetPassword(String tenantId, String userId, String newPassword) {
    return Mono.fromCallable(
            () -> {
              UserAccount account = requireOrdinaryUser(tenantId, userId);
              // The account already exists — update it. save() inserts and would violate the
              // primary key.
              return users.update(
                  new UserAccount(
                      account.userId(),
                      account.tenantId(),
                      account.username(),
                      account.email(),
                      passwordEncoder.encode(newPassword),
                      account.role(),
                      account.status(),
                      account.tokenVersion() + 1,
                      account.createdAt(),
                      Instant.now()));
            })
        .subscribeOn(jdbcScheduler);
  }

  public Mono<Void> delete(String tenantId, String userId) {
    return Mono.<Void>fromRunnable(
            () -> {
              requireOrdinaryUser(tenantId, userId);
              users.delete(tenantId, userId);
            })
        .subscribeOn(jdbcScheduler);
  }

  private UserAccount doCreate(String tenantId, CreateUserRequest req) {
    if (UserAccount.ROLE_ADMIN.equals(req.role())) {
      throw new ProtectedAdminAccountException();
    }
    if (users.existsByUsername(req.username())) {
      throw new ConflictException(ApiErrorCode.USERNAME_ALREADY_EXISTS);
    }
    Instant now = Instant.now();
    String role = req.role() != null ? req.role() : UserAccount.ROLE_USER;
    UserAccount account =
        new UserAccount(
            Ulid.next(),
            tenantId,
            req.username(),
            req.email(),
            passwordEncoder.encode(req.password()),
            role,
            1,
            1,
            now,
            now);
    return users.save(account);
  }

  private UserAccount doUpdate(String tenantId, String userId, UpdateUserRequest req) {
    UserAccount account = requireOrdinaryUser(tenantId, userId);
    if (UserAccount.ROLE_ADMIN.equals(req.role())) {
      throw new ProtectedAdminAccountException();
    }
    String role = req.role() != null ? req.role() : account.role();
    Integer status = req.status() != null ? Integer.valueOf(req.status()) : account.status();
    String email = req.email() != null ? req.email() : account.email();
    boolean invalidatesTokens = !role.equals(account.role()) || status != account.status();
    return users.update(
        new UserAccount(
            account.userId(),
            account.tenantId(),
            account.username(),
            email,
            account.passwordHash(),
            role,
            status,
            invalidatesTokens ? account.tokenVersion() + 1 : account.tokenVersion(),
            account.createdAt(),
            Instant.now()));
  }

  private UserAccount load(String tenantId, String userId) {
    return users
        .findByTenantIdAndUserId(tenantId, userId)
        .orElseThrow(() -> new NotFoundException("UserAccount", userId));
  }

  private UserAccount requireOrdinaryUser(String tenantId, String userId) {
    UserAccount account = load(tenantId, userId);
    if (account.isAdmin()) {
      throw new ProtectedAdminAccountException();
    }
    return account;
  }
}
