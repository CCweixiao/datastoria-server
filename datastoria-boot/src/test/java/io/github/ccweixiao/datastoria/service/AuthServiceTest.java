package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.ccweixiao.datastoria.common.config.SecurityProperties;
import io.github.ccweixiao.datastoria.common.crypto.MasterKeyProvider;
import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.common.error.BadCredentialsException;
import io.github.ccweixiao.datastoria.common.identity.JwtTokenService;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

import reactor.core.scheduler.Schedulers;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String MASTER_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

  @Mock private UserAccountRepository users;
  private final PasswordEncoder encoder = new BCryptPasswordEncoder();
  private AuthService authService;

  @BeforeEach
  void setUp() {
    MasterKeyProvider masterKey = new MasterKeyProvider(MASTER_KEY);
    JwtTokenService tokenService = new JwtTokenService(new SecurityProperties(), masterKey);
    authService = new AuthService(users, tokenService, encoder, Schedulers.immediate());
  }

  private UserAccount account(String username, String password, int status) {
    Instant now = Instant.now();
    return new UserAccount(
        "user-" + username,
        "default",
        username,
        null,
        encoder.encode(password),
        "USER",
        status,
        now,
        now);
  }

  @Test
  void loginSucceedsWithCorrectPassword() {
    when(users.findByUsername("alice")).thenReturn(Optional.of(account("alice", "s3cret", 1)));
    var response = authService.login("alice", "s3cret").block();
    assertThat(response).isNotNull();
    assertThat(response.token()).isNotBlank();
    assertThat(response.user().username()).isEqualTo("alice");
  }

  @Test
  void loginFailsWithWrongPassword() {
    when(users.findByUsername("alice")).thenReturn(Optional.of(account("alice", "s3cret", 1)));
    assertThatThrownBy(() -> authService.login("alice", "wrong").block())
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void loginFailsForUnknownUser() {
    when(users.findByUsername("ghost")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> authService.login("ghost", "anything").block())
        .isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void loginFailsForDisabledAccount() {
    when(users.findByUsername("alice")).thenReturn(Optional.of(account("alice", "s3cret", 0)));
    assertThatThrownBy(() -> authService.login("alice", "s3cret").block())
        .isInstanceOf(BadCredentialsException.class);
  }
}
