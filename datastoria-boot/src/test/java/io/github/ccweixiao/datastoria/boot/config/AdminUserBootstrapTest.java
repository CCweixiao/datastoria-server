package io.github.ccweixiao.datastoria.boot.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import io.github.ccweixiao.datastoria.common.config.SecurityProperties;
import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

@ExtendWith(MockitoExtension.class)
class AdminUserBootstrapTest {

  @Mock private UserAccountRepository users;

  private SecurityProperties properties() {
    SecurityProperties properties = new SecurityProperties();
    properties.getBootstrapAdmin().setUsername("admin");
    properties.getBootstrapAdmin().setPassword("admin123");
    properties.getBootstrapAdmin().setRole("ADMIN");
    return properties;
  }

  @Test
  void createsAdminWhenAbsent() {
    when(users.existsByUsername("admin")).thenReturn(false);
    when(users.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

    new AdminUserBootstrap(users, properties(), new BCryptPasswordEncoder()).run(null);

    verify(users).save(any(UserAccount.class));
  }

  @Test
  void isIdempotentWhenAdminAlreadyExists() {
    when(users.existsByUsername("admin")).thenReturn(true);

    new AdminUserBootstrap(users, properties(), new BCryptPasswordEncoder()).run(null);

    verify(users, never()).save(any(UserAccount.class));
  }

  @Test
  void skipsWhenNoPasswordConfigured() {
    SecurityProperties properties = new SecurityProperties();
    properties.getBootstrapAdmin().setUsername("admin");
    // password left at its default of "" (blank)
    when(users.existsByUsername("admin")).thenReturn(false);

    new AdminUserBootstrap(users, properties, new BCryptPasswordEncoder()).run(null);

    verify(users, never()).save(any(UserAccount.class));
  }
}
