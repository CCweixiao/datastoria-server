package io.github.ccweixiao.datastoria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.domain.Secret;
import io.github.ccweixiao.datastoria.common.dto.UserModelRequest;
import io.github.ccweixiao.datastoria.common.error.NotFoundException;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.dao.repository.ModelRepository;

import reactor.core.scheduler.Schedulers;

@ExtendWith(MockitoExtension.class)
class UserModelServiceTest {

  @Mock private ModelRepository models;
  @Mock private ModelProviderRepository providers;
  @Mock private SecretService secrets;
  private UserModelService service;
  private Identity identity;

  @BeforeEach
  void setUp() {
    service = new UserModelService(models, providers, secrets, Schedulers.immediate());
    identity = new Identity("tenant", "user-a", Set.of("ROLE_USER"));
  }

  @Test
  void createsModelOwnedByCurrentUserWithOwnedSecret() {
    when(providers.findAccessibleProviders("tenant", "user-a")).thenReturn(List.of(provider(null)));
    when(secrets.save("tenant", "user-a", "api_key", "sk-private", null)).thenReturn(secret());
    when(models.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service
        .create(
            new UserModelRequest("provider", "gpt-private", "My GPT", null, "sk-private"), identity)
        .block();

    ArgumentCaptor<Model> saved = ArgumentCaptor.forClass(Model.class);
    verify(models).save(saved.capture());
    assertThat(saved.getValue().ownerUserId()).isEqualTo("user-a");
    assertThat(saved.getValue().source()).isEqualTo("custom");
    assertThat(saved.getValue().secretId()).isEqualTo("secret");
  }

  @Test
  void usesPrivateProviderCredentialWithoutCreatingModelSecret() {
    when(providers.findAccessibleProviders("tenant", "user-a"))
        .thenReturn(List.of(provider("user-a")));
    when(models.save(any(Model.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service
        .create(new UserModelRequest("provider", "private-model", "Private", null, null), identity)
        .block();

    ArgumentCaptor<Model> saved = ArgumentCaptor.forClass(Model.class);
    verify(models).save(saved.capture());
    assertThat(saved.getValue().secretId()).isNull();
  }

  @Test
  void listsOnlyCurrentUsersModels() {
    when(models.findUserModels("tenant", "user-a")).thenReturn(List.of());

    assertThat(service.findAll(identity).block()).isEmpty();

    verify(models).findUserModels("tenant", "user-a");
  }

  @Test
  void cannotUpdateAnotherUsersModel() {
    when(models.findUserById("foreign", "tenant", "user-a")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service
                    .update(
                        "foreign",
                        0L,
                        new UserModelRequest("provider", "key", "name", null, null),
                        identity)
                    .block())
        .isInstanceOf(NotFoundException.class);
  }

  private static ModelProvider provider(String ownerUserId) {
    Instant now = Instant.now();
    return new ModelProvider(
        "provider",
        "tenant",
        ownerUserId,
        "openai",
        "OpenAI",
        null,
        "api_key",
        true,
        "{}",
        null,
        0,
        "admin",
        "admin",
        now,
        now,
        null);
  }

  private static Secret secret() {
    Instant now = Instant.now();
    return new Secret(
        "secret",
        "tenant",
        "user-a",
        "api_key",
        new byte[] {1},
        "v1",
        new byte[] {2},
        "sk-…vate",
        null,
        now,
        now,
        null);
  }
}
