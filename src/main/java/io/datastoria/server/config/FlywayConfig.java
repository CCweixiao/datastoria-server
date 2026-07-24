package io.datastoria.server.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Guards Flyway migration location selection.
 *
 * <p>The {@code prod} profile must only load MySQL migrations and must fail fast if the datasource
 * URL is not {@code jdbc:mysql:}. There is no automatic fallback to SQLite in production.
 */
@Configuration
public class FlywayConfig {

  @Bean
  @Profile("prod")
  ProdDatasourceGuard prodDatasourceGuard(DataSourceProperties properties) {
    String url = properties.getUrl();
    if (url == null || !url.startsWith("jdbc:mysql:")) {
      throw new IllegalStateException(
          "Production profile requires a MySQL datasource URL starting with"
              + " 'jdbc:mysql:'. Refusing to start to avoid SQLite fallback.");
    }
    return new ProdDatasourceGuard(url);
  }

  record ProdDatasourceGuard(String url) {}
}
