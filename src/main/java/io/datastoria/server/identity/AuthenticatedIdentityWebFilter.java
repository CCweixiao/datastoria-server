package io.datastoria.server.identity;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/** Places the authenticated OAuth2/OIDC principal in the application Reactor identity context. */
@Component
@Order(0)
@Profile("prod")
public class AuthenticatedIdentityWebFilter implements WebFilter {

  private final String defaultTenant;

  public AuthenticatedIdentityWebFilter(
      @Value("${datastoria.identity.default-tenant:tenant-default}") String defaultTenant) {
    this.defaultTenant = defaultTenant;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return exchange
        .getPrincipal()
        .ofType(Authentication.class)
        .filter(Authentication::isAuthenticated)
        .map(this::identity)
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .flatMap(
            identity ->
                identity
                    .map(
                        value ->
                            chain
                                .filter(exchange)
                                .contextWrite(Context.of(IdentityWebFilter.CONTEXT_KEY, value)))
                    .orElseGet(() -> chain.filter(exchange)));
  }

  private Identity identity(Authentication authentication) {
    Map<String, Object> attributes =
        authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal
            ? principal.getAttributes()
            : Map.of();
    String userId =
        firstNonBlank(
            string(attributes.get("email")),
            string(attributes.get("preferred_username")),
            string(attributes.get("sub")),
            authentication.getName());
    String tenantId =
        firstNonBlank(
            string(attributes.get("tenant_id")), string(attributes.get("tid")), defaultTenant);
    Set<String> roles = new LinkedHashSet<>();
    authentication.getAuthorities().forEach(authority -> roles.add(authority.getAuthority()));
    addRoles(roles, attributes.get("roles"));
    addRoles(roles, attributes.get("groups"));
    roles.add("ROLE_USER");
    return new Identity(tenantId, userId, Set.copyOf(roles));
  }

  private static void addRoles(Set<String> roles, Object value) {
    if (value instanceof Iterable<?> values) {
      values.forEach(entry -> addRole(roles, string(entry)));
    } else if (value instanceof String text) {
      for (String entry : text.split("[, ]")) {
        addRole(roles, entry);
      }
    }
  }

  private static void addRole(Set<String> roles, String value) {
    if (value != null && !value.isBlank()) {
      roles.add(value.startsWith("ROLE_") ? value : "ROLE_" + value.toUpperCase());
    }
  }

  private static String string(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    throw new IllegalStateException("Authenticated principal has no stable user identifier");
  }
}
