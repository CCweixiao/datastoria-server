package io.github.ccweixiao.datastoria.boot.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Guards Flyway migration location selection.
 *
 * <p>Production database profiles must load only their matching migrations and fail fast when the
 * datasource URL uses another dialect. There is no automatic fallback to SQLite.
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
