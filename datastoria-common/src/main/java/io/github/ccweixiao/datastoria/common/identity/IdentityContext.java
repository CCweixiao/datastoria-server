package io.github.ccweixiao.datastoria.common.identity;

import reactor.core.publisher.Mono;

/**
 * Helper to extract the {@link Identity} from the Reactor {@link reactor.util.context.Context}.
 * Controllers and services use {@link #current()} to obtain the resolved identity in a non-blocking
 * chain.
 */
public final class IdentityContext {

  /**
   * Reactor context key under which the resolved {@link Identity} is published by the auth filter.
   */
  public static final String CONTEXT_KEY = "datastoria.identity";

  /**
   * Exchange attribute key under which an upstream server-side filter may pre-resolve an {@link
   * Identity} from another trusted source. Exchange attributes are server-internal per-request
   * state, never populated from the HTTP request, so this is not reachable by a remote caller.
   */
  public static final String PRE_RESOLVED_KEY = "datastoria.identity.preResolved";

  private IdentityContext() {}

  public static Mono<Identity> current() {
    return Mono.deferContextual(
        ctx -> {
          Object value = ctx.get(CONTEXT_KEY);
          if (value instanceof Identity identity) {
            return Mono.just(identity);
          }
          return Mono.error(
              new IllegalStateException(
                  "No Identity in Reactor context; JwtIdentityWebFilter did not run."));
        });
  }
}
