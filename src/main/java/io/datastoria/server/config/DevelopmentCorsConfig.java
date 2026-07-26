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
      @Value("${datastoria.cors.allowed-origins:" + "http://localhost:3000,http://127.0.0.1:3000}")
          List<String> origins) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Accept",
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "If-Match",
            "Last-Event-ID",
            "X-Session-Share-Code",
            "x-datastoria-user-email"));
    configuration.setExposedHeaders(
        List.of(
            "Deprecation",
            "ETag",
            "Link",
            "Location",
            "X-ClickHouse-Exception-Code",
            "X-ClickHouse-Format",
            "X-ClickHouse-Progress",
            "X-ClickHouse-Query-Id",
            "X-ClickHouse-Server-Display-Name",
            "X-ClickHouse-Summary",
            "X-ClickHouse-Timezone",
            "X-Vercel-AI-UI-Message-Stream"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
