package io.datastoria.server.identity;

import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Reads the {@code x-datastoria-user-email} header, resolves a server-side {@link Identity} and
 * places it in the Reactor {@link Context} so downstream controllers and services can access it via
 * {@link IdentityContext#current()}. Also enforces coarse-grained RBAC: any path under {@code
 * /api/admin/} requires {@code ROLE_ADMIN} on the resolved identity. P10 will replace this with
 * proper OAuth-backed Spring Security authorization.
 */
@Component
@Order(-200)
@Profile({"local", "test"})
public class IdentityWebFilter implements WebFilter {

  static final String CONTEXT_KEY = "datastoria.identity";
  static final String ADMIN_PREFIX = "/api/admin/";

  private final DevIdentityResolver resolver;

  public IdentityWebFilter(DevIdentityResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    HttpHeaders headers = exchange.getRequest().getHeaders();
    String email = headers.getFirst("x-datastoria-user-email");
    Identity identity = resolver.resolve(email);
    String path = exchange.getRequest().getPath().pathWithinApplication().value();
    if (path.startsWith(ADMIN_PREFIX) && !identity.isAdmin()) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "ROLE_ADMIN required"));
    }
    Context context = Context.of(CONTEXT_KEY, identity);
    return chain.filter(exchange).contextWrite(context);
  }
}
