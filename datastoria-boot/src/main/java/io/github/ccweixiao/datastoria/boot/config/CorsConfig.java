package io.github.ccweixiao.datastoria.boot.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS policy for a separately hosted Next.js UI.
 *
 * <p>The unified single-process deployment serves the frontend same-origin and needs no CORS; an
 * empty {@code datastoria.cors.allowed-origins} (the prod default) registers nothing, which denies
 * all cross-origin access. Set the property only for a separately hosted frontend.
 */
@Configuration
@Profile({"dev", "prod"})
public class CorsConfig {

  @Bean
  UrlBasedCorsConfigurationSource corsConfigurationSource(
      @Value("${datastoria.cors.allowed-origins:}") List<String> origins) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    if (origins.isEmpty()) {
      return source;
    }
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
            // Transitional: the Next.js API wrapper still attaches this legacy header to every
            // request. The backend ignores it (auth is JWT-only now); it is allowed here solely so
            // the browser preflight does not block calls during the frontend migration. Remove once
            // the frontend stops sending it.
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
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
