package io.github.ccweixiao.datastoria.common.identity;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * Authenticates every request with a Bearer JWT (the same mechanism in dev and prod). On success it
 * publishes the resolved {@link Identity} into the Reactor context under {@link
 * IdentityContext#CONTEXT_KEY} so controllers/services read it via {@link
 * IdentityContext#current()}.
 *
 * <p>Extension hook: an upstream server-side filter may place an {@link Identity} into the {@link
 * IdentityContext#PRE_RESOLVED_KEY} exchange attribute to supply identity from another trusted
 * source; this filter then honours it verbatim. Exchange attributes are in-memory per-request
 * server state — they are never populated from the HTTP request, so this hook is not reachable by a
 * remote caller and does not weaken production auth.
 *
 * <ul>
 *   <li>CORS preflight and the public paths ({@code /api/auth/login}, actuator health/info) skip
 *       auth.
 *   <li>Any other path without a valid token (and without a pre-resolved identity) is rejected with
 *       {@code text/plain} 401 (written directly because filter-thrown exceptions bypass
 *       {@code @RestControllerAdvice}).
 *   <li>Paths under {@code /api/admin/} additionally require {@code ROLE_ADMIN}.
 * </ul>
 */
@Component
@Order(-200)
public class JwtIdentityWebFilter implements WebFilter {

  static final String ADMIN_PREFIX = "/api/admin/";
  static final String UNAUTHENTICATED_BODY = "Authentication required";

  private final JwtTokenService tokenService;

  public JwtIdentityWebFilter(JwtTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getPath().pathWithinApplication().value();
    if (isPublic(exchange, path)) {
      return chain.filter(exchange);
    }
    Identity preResolved = exchange.getAttribute(IdentityContext.PRE_RESOLVED_KEY);
    if (preResolved != null) {
      return enforce(preResolved, path, exchange, chain);
    }
    Optional<JwtTokenService.VerifiedToken> verified =
        tokenService.parseAndVerify(extractBearer(exchange));
    if (verified.isEmpty()) {
      return writeUnauthorized(exchange);
    }
    JwtTokenService.VerifiedToken token = verified.get();
    Set<String> roles = rolesFor(token.role());
    Identity identity =
        new Identity(
            token.tenantId() != null ? token.tenantId() : "default", token.userId(), roles);
    return enforce(identity, path, exchange, chain);
  }

  private static Mono<Void> enforce(
      Identity identity, String path, ServerWebExchange exchange, WebFilterChain chain) {
    if (path.startsWith(ADMIN_PREFIX) && !identity.isAdmin()) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "ROLE_ADMIN required"));
    }
    return chain.filter(exchange).contextWrite(Context.of(IdentityContext.CONTEXT_KEY, identity));
  }

  private static boolean isPublic(ServerWebExchange exchange, String path) {
    HttpMethod method = exchange.getRequest().getMethod();
    if (method != null && method.equals(HttpMethod.OPTIONS)) {
      return true;
    }
    return path.equals("/api/auth/login")
        || path.startsWith("/actuator/health")
        || path.equals("/actuator/info");
  }

  private static String extractBearer(ServerWebExchange exchange) {
    String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return null;
    }
    String token = header.substring(7).trim();
    return token.isEmpty() ? null : token;
  }

  private static Set<String> rolesFor(String role) {
    return "ADMIN".equalsIgnoreCase(role) ? Set.of("ROLE_ADMIN", "ROLE_USER") : Set.of("ROLE_USER");
  }

  private static Mono<Void> writeUnauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
    DataBuffer buffer =
        exchange
            .getResponse()
            .bufferFactory()
            .wrap(UNAUTHENTICATED_BODY.getBytes(StandardCharsets.UTF_8));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
