package io.github.ccweixiao.datastoria.common.identity;

import reactor.core.publisher.Mono;

/** Resolves a verified JWT to the current enabled account identity. */
public interface TokenAccountResolver {

  Mono<Identity> resolve(JwtTokenService.VerifiedToken token);
}
