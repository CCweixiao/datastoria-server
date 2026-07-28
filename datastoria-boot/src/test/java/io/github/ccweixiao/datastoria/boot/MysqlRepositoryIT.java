package io.github.ccweixiao.datastoria.boot;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

/** Executes the shared repository contract against real MySQL 8.0 when Docker is available. */
@SpringBootTest
@ActiveProfiles("mysql-it")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MysqlRepositoryIT extends RelationalRepositoryContractIT {

  private static MySQLContainer<?> mysql;
  private static boolean dockerAvailable;

  @DynamicPropertySource
  static void configureDataSource(DynamicPropertyRegistry registry) {
    dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
    if (!dockerAvailable) {
      useSqliteFallback(registry);
      return;
    }
    mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("datastoria_it");
    mysql.start();
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration/mysql");
  }

  private static void useSqliteFallback(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> "jdbc:sqlite::memory:");
    registry.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
    registry.add("spring.flyway.locations", () -> "classpath:db/migration/sqlite");
  }

  @BeforeAll
  void checkDocker() {
    Assumptions.assumeTrue(dockerAvailable, "Docker unavailable — skipping MySQL IT");
  }

  @Override
  protected String tenantId() {
    return "tenant-mysql-it";
  }
}
