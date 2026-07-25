package io.datastoria.server.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/** CORS policy for a separately hosted Next.js UI. */
@Configuration
@Profile({"local", "test", "prod"})
public class DevelopmentCorsConfig {

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${datastoria.cors.allowed-origins:http://localhost:3000}") List<String> origins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Accept",
            "Authorization",
            "Content-Type",
            "If-Match",
            "Last-Event-ID",
            "x-datastoria-user-email"));
    configuration.setExposedHeaders(List.of("ETag"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
