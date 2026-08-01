package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.ccweixiao.datastoria.common.domain.UserAccount;
import io.github.ccweixiao.datastoria.common.dto.CreateUserRequest;
import io.github.ccweixiao.datastoria.common.dto.UpdateUserRequest;
import io.github.ccweixiao.datastoria.common.error.ProtectedAdminAccountException;
import io.github.ccweixiao.datastoria.dao.repository.UserAccountRepository;

import reactor.core.scheduler.Schedulers;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTest {

  @Mock private UserAccountRepository users;
  @Mock private PasswordEncoder passwordEncoder;
  private UserAccountService service;

  @BeforeEach
  void setUp() {
    service = new UserAccountService(users, passwordEncoder, Schedulers.immediate());
  }

  @Test
  void listContainsOnlyOrdinaryUsers() {
    when(users.findAll("tenant"))
        .thenReturn(List.of(account("admin", "ADMIN"), account("user", "USER")));

    List<UserAccount> result = service.findAll("tenant").block();

    assertThat(result).extracting(UserAccount::username).containsExactly("user");
  }

  @Test
  void cannotCreateAdministratorThroughOrdinaryUserApi() {
    CreateUserRequest request = new CreateUserRequest("other-admin", "password123", null, "ADMIN");

    assertThatThrownBy(() -> service.create("tenant", request).block())
        .isInstanceOf(ProtectedAdminAccountException.class);
    verifyNoInteractions(users, passwordEncoder);
  }

  @Test
  void administratorAccountsCannotBeUpdatedResetOrDeleted() {
    UserAccount admin = account("admin", "ADMIN");
    when(users.findByTenantIdAndUserId("tenant", admin.userId())).thenReturn(Optional.of(admin));

    assertThatThrownBy(
            () ->
                service
                    .update("tenant", admin.userId(), new UpdateUserRequest("USER", "1", null))
                    .block())
        .isInstanceOf(ProtectedAdminAccountException.class);
    assertThatThrownBy(() -> service.resetPassword("tenant", admin.userId(), "password123").block())
        .isInstanceOf(ProtectedAdminAccountException.class);
    assertThatThrownBy(() -> service.delete("tenant", admin.userId()).block())
        .isInstanceOf(ProtectedAdminAccountException.class);
  }

  @Test
  void administratorCanLoadOwnAccount() {
    UserAccount admin = account("admin", "ADMIN");
    when(users.findByTenantIdAndUserId("tenant", admin.userId())).thenReturn(Optional.of(admin));

    UserAccount result = service.findCurrentAccount("tenant", admin.userId()).block();

    assertThat(result).isEqualTo(admin);
  }

  @Test
  void deletesOrdinaryUser() {
    UserAccount user = account("user", "USER");
    when(users.findByTenantIdAndUserId("tenant", user.userId())).thenReturn(Optional.of(user));

    service.delete("tenant", user.userId()).block();

    verify(users).delete("tenant", user.userId());
  }

  private static UserAccount account(String username, String role) {
    Instant now = Instant.now();
    return new UserAccount(
        username + "-id", "tenant", username, null, "hash", role, 1, 1, now, now);
  }
}
