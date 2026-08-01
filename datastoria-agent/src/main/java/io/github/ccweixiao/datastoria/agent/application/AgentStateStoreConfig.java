package io.github.ccweixiao.datastoria.agent.application;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.agentscope.core.state.AgentStateStore;
import io.agentscope.extensions.mysql.state.MysqlAgentStateStore;

/**
 * Wires the single supported AgentScope state store: MySQL. Development and production share the
 * same persistence implementation and schema; only their connection and security settings differ.
 *
 * <p>The {@code ds_agentscope_sessions} table is Flyway-owned (V16); the store is constructed with
 * {@code createIfNotExist=false} so it only verifies the table and needs no runtime DDL privileges.
 */
@Configuration
public class AgentStateStoreConfig {

  /** Must match the table created by Flyway migration V16. */
  static final String TABLE_NAME = "ds_agentscope_sessions";

  /**
   * AgentScope session state in the DataStoria MySQL database, sharing the primary Hikari-pooled
   * {@link DataSource}.
   */
  @Bean
  AgentStateStore mysqlAgentStateStore(
      DataSource dataSource,
      @Value("${datastoria.agent.state-store.mysql.database-name:}")
          String configuredDatabaseName) {
    String databaseName =
        configuredDatabaseName == null || configuredDatabaseName.isBlank()
            ? resolveCatalog(dataSource)
            : configuredDatabaseName.trim();
    // createIfNotExist=false: Flyway owns the schema; the store only verifies the table exists.
    return new MysqlAgentStateStore(dataSource, databaseName, TABLE_NAME, false);
  }

  /**
   * Reads the current catalog (the database named in the JDBC URL) from the physical connection, so
   * operators need not add another environment variable. Overridable via {@code
   * datastoria.agent.state-store.mysql.database-name} for non-standard URLs.
   */
  private static String resolveCatalog(DataSource dataSource) {
    try (var conn = dataSource.getConnection()) {
      return conn.getCatalog();
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Unable to resolve MySQL catalog for the AgentScope state store; "
              + "set datastoria.agent.state-store.mysql.database-name explicitly",
          e);
    }
  }
}
