package io.github.ccweixiao.datastoria.boot.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * SQLite datasource for the {@code local} and {@code test} profiles.
 *
 * <p>Applies the required PRAGMAs ({@code foreign_keys=ON}, {@code busy_timeout}, WAL journal mode)
 * on every pooled connection. WAL is skipped for in-memory databases where it is not applicable.
 * SQLite serializes writes, so the pool is pinned to a single connection to avoid {@code
 * SQLITE_BUSY} contention.
 */
@Configuration
@Profile({"local", "test"})
public class DataSourceConfig {

  @Bean
  DataSource sqliteDataSource(DataSourceProperties properties) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(properties.getUrl());
    String driver = properties.getDriverClassName();
    if (driver != null) {
      config.setDriverClassName(driver);
    }
    if (properties.getUsername() != null) {
      config.setUsername(properties.getUsername());
    }
    if (properties.getPassword() != null) {
      config.setPassword(properties.getPassword());
    }
    config.setMaximumPoolSize(1);
    config.setMinimumIdle(1);
    config.setPoolName("datastoria-sqlite");
    // Per-connection PRAGMAs applied by the SQLite JDBC driver.
    config.addDataSourceProperty("foreign_keys", "true");
    config.addDataSourceProperty("busy_timeout", "5000");
    String url = properties.getUrl();
    if (url != null && !url.contains(":memory:")) {
      config.addDataSourceProperty("journal_mode", "WAL");
      config.addDataSourceProperty("synchronous", "NORMAL");
    }
    return new HikariDataSource(config);
  }
}
