package io.datastoria.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;

/**
 * OAuth2/OIDC-backed production security. Development identity headers are never consulted in this
 * profile; user identity is derived exclusively from the authenticated Spring Security principal.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("prod")
public class ProductionSecurityConfig {

  @Bean
  SecurityWebFilterChain productionSecurityFilterChain(
      ServerHttpSecurity http,
      @Value("${datastoria.auth.success-url:http://localhost:3000}") String successUrl) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(org.springframework.security.config.Customizer.withDefaults())
        .authorizeExchange(
            exchange ->
                exchange
                    .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .pathMatchers(
                        "/oauth2/**",
                        "/login/**",
                        "/api/auth/providers",
                        "/api/auth/session",
                        "/api/auth/signin/**")
                    .permitAll()
                    .pathMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyExchange()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors.authenticationEntryPoint(
                    new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
        .oauth2Login(
            login ->
                login.authenticationSuccessHandler(
                    new RedirectServerAuthenticationSuccessHandler(successUrl)))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/signout")
                    .logoutSuccessHandler(
                        (exchange, authentication) -> {
                          exchange.getExchange().getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                          return exchange.getExchange().getResponse().setComplete();
                        }))
        .build();
  }
}
