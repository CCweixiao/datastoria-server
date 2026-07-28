package io.github.ccweixiao.datastoria.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Provides a dedicated bounded-elastic {@link Scheduler} for blocking JDBC calls so they never run
 * on the Netty event loop. All repository-backed service methods must subscribe to this scheduler.
 */
@Configuration
public class JdbcSchedulerConfig {

  public static final String JDBC_SCHEDULER = "jdbcScheduler";

  @Bean(name = JDBC_SCHEDULER, destroyMethod = "dispose")
  Scheduler jdbcScheduler() {
    return Schedulers.newBoundedElastic(32, 1024, "datastoria-jdbc");
  }
}
