package io.datastoria.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Fail-closed production security until OAuth-backed authentication is implemented in P10.
 *
 * <p>Development identity headers must never authorize a production request. Health probes remain
 * available, while every application and administrative endpoint is denied.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("prod")
public class ProductionSecurityConfig {

  @Bean
  SecurityWebFilterChain productionSecurityFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyExchange()
                    .denyAll())
        .build();
  }
}
