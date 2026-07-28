package io.github.ccweixiao.datastoria.common.identity;

import reactor.core.publisher.Mono;

/**
 * Helper to extract the {@link Identity} from the Reactor {@link reactor.util.context.Context}.
 * Controllers and services use {@link #current()} to obtain the resolved identity in a non-blocking
 * chain.
 */
public final class IdentityContext {

  private IdentityContext() {}

  public static Mono<Identity> current() {
    return Mono.deferContextual(
        ctx -> {
          Object value = ctx.get(IdentityWebFilter.CONTEXT_KEY);
          if (value instanceof Identity identity) {
            return Mono.just(identity);
          }
          return Mono.error(
              new IllegalStateException(
                  "No Identity in Reactor context; IdentityWebFilter did not" + " run."));
        });
  }
}
