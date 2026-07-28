package io.github.ccweixiao.datastoria.common.identity;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
  static final String UNAUTHENTICATED_BODY = "Authentication required";

  private final DevIdentityResolver resolver;
  private final boolean allowAnonymous;

  public IdentityWebFilter(
      DevIdentityResolver resolver,
      @Value("${datastoria.identity.allow-anonymous:true}") boolean allowAnonymous) {
    this.resolver = resolver;
    this.allowAnonymous = allowAnonymous;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    HttpHeaders headers = exchange.getRequest().getHeaders();
    String email = headers.getFirst("x-datastoria-user-email");
    if ((email == null || email.isBlank()) && !allowAnonymous) {
      // Prod-like behaviour: missing header is unauthenticated. Writes a Node-compatible
      // text/plain body directly since exceptions thrown from filters bypass the
      // @RestControllerAdvice that emits PlainTextException responses. Used by the P3
      // unauthenticated wire-format tests (A03-list-unauthenticated, A09-share-unauthenticated).
      exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
      exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
      DataBuffer buffer =
          exchange
              .getResponse()
              .bufferFactory()
              .wrap(UNAUTHENTICATED_BODY.getBytes(StandardCharsets.UTF_8));
      return exchange.getResponse().writeWith(Mono.just(buffer));
    }
    Identity identity = resolver.resolve(email);
    String path = exchange.getRequest().getPath().pathWithinApplication().value();
    if (path.startsWith(ADMIN_PREFIX) && !identity.isAdmin()) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "ROLE_ADMIN required"));
    }
    Context context = Context.of(CONTEXT_KEY, identity);
    return chain.filter(exchange).contextWrite(context);
  }
}
