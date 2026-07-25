package io.datastoria.server.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.service.SecretService;

class OpenAiModelAdapterProviderTest {

  @Test
  void decryptsServerSideSecretAndBuildsOfficialStreamingModel() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(Optional.of(provider("openai", "https://example.test/v1", "provider-secret")));
    when(secrets.decrypt("model-secret", "tenant-1")).thenReturn("sk-server-only");

    ModelAdapter adapter =
        new OpenAiModelAdapterProvider(providers, secrets).adapterFor(model("model-secret"));

    assertThat(adapter.modelFor(null)).isInstanceOf(OpenAIChatModel.class);
    verify(secrets).decrypt("model-secret", "tenant-1");
  }

  @Test
  void rejectsUnsupportedProviderBeforeDecrypting() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(Optional.of(provider("anthropic", null, "provider-secret")));

    assertThatThrownBy(
            () -> new OpenAiModelAdapterProvider(providers, secrets).adapterFor(model(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported provider type");
  }

  private static Model model(String secretId) {
    Instant now = Instant.now();
    return new Model(
        "model-1",
        "tenant-1",
        "provider-1",
        "gpt-test",
        "GPT",
        null,
        "manual",
        true,
        false,
        "{}",
        "{}",
        secretId,
        0,
        now,
        now,
        null);
  }

  private static ModelProvider provider(String key, String baseUrl, String secretId) {
    Instant now = Instant.now();
    return new ModelProvider(
        "provider-1",
        "tenant-1",
        key,
        "Provider",
        baseUrl,
        "api_key",
        true,
        "{}",
        secretId,
        0,
        "admin",
        "admin",
        now,
        now,
        null);
  }
}
