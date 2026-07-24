package io.datastoria.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * WebFlux security configuration.
 *
 * <p>P2.1 baseline permits all exchanges so dev/test can exercise the new admin and user endpoints
 * without OAuth (real auth arrives in P10). The dev {@link io.datastoria.server.identity.Identity}
 * is resolved from a header by {@link io.datastoria.server.identity.IdentityWebFilter}. P2.9
 * tightens admin endpoints to {@code ROLE_ADMIN}.
 */
@Configuration
@EnableWebFluxSecurity
@Profile({"local", "test", "mysql-it"})
public class SecurityConfig {

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(org.springframework.security.config.Customizer.withDefaults())
        .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
        .build();
  }
}
