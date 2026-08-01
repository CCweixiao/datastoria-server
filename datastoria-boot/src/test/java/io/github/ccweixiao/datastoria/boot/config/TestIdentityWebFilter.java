package io.github.ccweixiao.datastoria.boot.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.common.identity.IdentityContext;

import reactor.core.publisher.Mono;

/**
 * Test-only filter (lives under {@code src/test}, so it is absent from production) that revives the
 * {@code x-datastoria-user-email} dev header for integration tests. It resolves an {@link Identity}
 * the same way the deleted {@code DevIdentityResolver} did and publishes it via the safe {@link
 * IdentityContext#PRE_RESOLVED_KEY} exchange attribute, which {@code JwtIdentityWebFilter} honours.
 * This keeps the dozens of header-based tests working without minting JWTs, while production
 * remains JWT-only.
 */
@Component
@Order(-300)
public class TestIdentityWebFilter implements WebFilter {

  private final String defaultTenant;
  private final String defaultUser;
  private final String defaultRoles;
  private final Set<String> adminUsers;
  private final boolean allowAnonymous;

  public TestIdentityWebFilter(
      @Value("${datastoria.identity.default-tenant:tenant-default}") String defaultTenant,
      @Value("${datastoria.identity.default-user:}") String defaultUser,
      @Value("${datastoria.identity.default-roles:ROLE_USER}") String defaultRoles,
      @Value("${datastoria.identity.admin-users:dev@example.com}") String adminUsers,
      @Value("${datastoria.identity.allow-anonymous:true}") boolean allowAnonymous) {
    this.defaultTenant = defaultTenant;
    this.defaultUser = defaultUser;
    this.defaultRoles = defaultRoles;
    this.adminUsers =
        Arrays.stream(adminUsers.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    this.allowAnonymous = allowAnonymous;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String email = exchange.getRequest().getHeaders().getFirst("x-datastoria-user-email");
    boolean missing = email == null || email.isBlank();
    if (missing && !allowAnonymous) {
      // Let JwtIdentityWebFilter reject with 401.
      return chain.filter(exchange);
    }
    String userId = missing ? defaultUser : email;
    if (userId == null || userId.isBlank()) {
      return chain.filter(exchange);
    }
    String tenant = resolveTenant(userId);
    Set<String> roles = resolveRoles(userId);
    exchange
        .getAttributes()
        .put(IdentityContext.PRE_RESOLVED_KEY, new Identity(tenant, userId, roles));
    return chain.filter(exchange);
  }

  private String resolveTenant(String userId) {
    if (userId.contains("@")) {
      String prefix = userId.substring(0, userId.indexOf('@'));
      if (prefix.startsWith("tenant-")) {
        return prefix;
      }
    }
    return defaultTenant;
  }

  private Set<String> roles(String source) {
    return Arrays.stream(source.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

  private Set<String> resolveRoles(String userId) {
    return roles(adminUsers.contains(userId) ? defaultRoles : "ROLE_USER");
  }
}
