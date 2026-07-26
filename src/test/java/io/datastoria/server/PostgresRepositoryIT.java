package io.datastoria.server;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/** Executes the shared repository contract against real PostgreSQL 16 when Docker is available. */
@SpringBootTest
@ActiveProfiles("postgres-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRepositoryIT extends RelationalRepositoryContractIT {

  private static PostgreSQLContainer<?> postgres;
  private static boolean dockerAvailable;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
    if (!dockerAvailable) {
      useSqliteFallback(registry);
      return;
    }
    postgres = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("datastoria_it");
    postgres.start();
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration/postgresql");
  }

  private static void useSqliteFallback(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite::memory:");
    registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration/sqlite");
  }

  @BeforeAll
  void checkDocker() {
    Assumptions.assumeTrue(dockerAvailable, "Docker unavailable — skipping PostgreSQL IT");
  }

  @Override
  protected String tenantId() {
    return "tenant-postgres-it";
  }
}
