package io.datastoria.server.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/** CORS policy for the separately hosted Next.js development UI. */
@Configuration
@Profile({"local", "test"})
public class DevelopmentCorsConfig {

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${datastoria.cors.allowed-origins:http://localhost:3000}") List<String> origins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "If-Match", "x-datastoria-user-email"));
    configuration.setExposedHeaders(List.of("ETag"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
