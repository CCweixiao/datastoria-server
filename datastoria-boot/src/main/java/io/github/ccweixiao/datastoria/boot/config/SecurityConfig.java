package io.github.ccweixiao.datastoria.boot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * WebFlux security configuration, identical in dev and prod. The chain permits all exchanges and
 * delegates authentication + coarse-grained RBAC to {@link
 * io.github.ccweixiao.datastoria.common.identity.JwtIdentityWebFilter}, which validates the Bearer
 * JWT and publishes the resolved {@link io.github.ccweixiao.datastoria.common.identity.Identity}
 * into the Reactor context.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(org.springframework.security.config.Customizer.withDefaults())
        .authorizeExchange(exchange -> exchange.anyExchange().permitAll())
        .build();
  }

  /** BCrypt encoder shared by login verification and user-account management. */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
