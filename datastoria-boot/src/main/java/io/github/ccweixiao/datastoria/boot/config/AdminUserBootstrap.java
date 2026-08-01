package io.github.ccweixiao.datastoria.boot.config;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.ccweixiao.datastoria.common.config.SecurityProperties;
import io.github.ccweixiao.datastoria.common.domain.Ulid;
import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

/**
 * Idempotently creates the bootstrap administrator on startup. Skips when the configured username
 * already exists or when no password is configured. Runs after Flyway has applied migrations, so
 * the table is present.
 */
@Component
public class AdminUserBootstrap implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);

  private final UserAccountRepository users;
  private final SecurityProperties properties;
  private final PasswordEncoder passwordEncoder;

  public AdminUserBootstrap(
      UserAccountRepository users, SecurityProperties properties, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.properties = properties;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    SecurityProperties.BootstrapAdmin cfg = properties.getBootstrapAdmin();
    if (users.existsByUsername(cfg.getUsername())) {
      return;
    }
    String password = cfg.getPassword();
    if (password == null || password.isBlank()) {
      log.warn(
          "Skipping admin bootstrap: datastoria.security.bootstrap-admin.password is not set.");
      return;
    }
    String tenant = cfg.getTenant() != null ? cfg.getTenant() : properties.getDefaultTenant();
    Instant now = Instant.now();
    UserAccount admin =
        new UserAccount(
            Ulid.next(),
            tenant,
            cfg.getUsername(),
            cfg.getEmail(),
            passwordEncoder.encode(password),
            cfg.getRole() != null ? cfg.getRole() : UserAccount.ROLE_ADMIN,
            1,
            1,
            now,
            now);
    users.save(admin);
    log.info("Bootstrapped admin user '{}' (tenant={}).", cfg.getUsername(), tenant);
  }
}
