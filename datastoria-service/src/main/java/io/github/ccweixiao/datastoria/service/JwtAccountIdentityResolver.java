package io.github.ccweixiao.datastoria.service;

import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import io.github.ccweixiao.datastoria.common.config.JdbcSchedulerConfig;
import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.common.identity.JwtTokenService;
import io.github.ccweixiao.datastoria.common.identity.TokenAccountResolver;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/** Resolves JWT subjects against current account status, role, tenant and token version. */
@Service
public class JwtAccountIdentityResolver implements TokenAccountResolver {

  private final UserAccountRepository users;
  private final Scheduler jdbcScheduler;

  public JwtAccountIdentityResolver(
      UserAccountRepository users,
      @Qualifier(JdbcSchedulerConfig.JDBC_SCHEDULER) Scheduler jdbcScheduler) {
    this.users = users;
    this.jdbcScheduler = jdbcScheduler;
  }

  @Override
  public Mono<Identity> resolve(JwtTokenService.VerifiedToken token) {
    return Mono.fromCallable(() -> users.findByUserId(token.userId()).orElse(null))
        .subscribeOn(jdbcScheduler)
        .filter(
            account -> account.enabled() && account.tokenVersion() == token.tokenVersion())
        .map(
            account ->
                new Identity(
                    account.tenantId(),
                    account.userId(),
                    account.isAdmin()
                        ? Set.of("ROLE_ADMIN", "ROLE_USER")
                        : Set.of("ROLE_USER")));
  }
}
