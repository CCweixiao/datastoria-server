package io.github.ccweixiao.datastoria.boot.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fails fast unless the configured datasource is MySQL. MySQL 5.7 is the project's single database
 * baseline, so there is no dialect fallback.
 */
@Configuration
public class FlywayConfig {

  @Bean
  MysqlDatasourceGuard mysqlDatasourceGuard(DataSourceProperties properties) {
    String url = properties.getUrl();
    if (url == null || !url.startsWith("jdbc:mysql:")) {
      throw new IllegalStateException(
          "DataStoria requires a MySQL datasource URL starting with 'jdbc:mysql:'.");
    }
    return new MysqlDatasourceGuard(url);
  }

  record MysqlDatasourceGuard(String url) {}
}
