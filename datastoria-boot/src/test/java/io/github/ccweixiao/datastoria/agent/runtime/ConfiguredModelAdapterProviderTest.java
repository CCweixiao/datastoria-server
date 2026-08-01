package io.github.ccweixiao.datastoria.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.extensions.model.anthropic.AnthropicChatModel;
import io.agentscope.extensions.model.gemini.GeminiChatModel;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.github.ccweixiao.datastoria.common.domain.Model;
import io.github.ccweixiao.datastoria.common.domain.ModelProvider;
import io.github.ccweixiao.datastoria.common.identity.Identity;
import io.github.ccweixiao.datastoria.dao.repository.ModelProviderRepository;
import io.github.ccweixiao.datastoria.service.OAuthCredentialService;
import io.github.ccweixiao.datastoria.service.SecretService;

class ConfiguredModelAdapterProviderTest {

  @Test
  void decryptsServerSideSecretAndBuildsOfficialStreamingModel() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(Optional.of(provider("openai", "https://example.test/v1", "provider-secret")));
    when(secrets.decrypt("model-secret", "tenant-1")).thenReturn("sk-server-only");

    ModelAdapter adapter =
        new ConfiguredModelAdapterProvider(
                providers, secrets, mock(OAuthCredentialService.class), new ObjectMapper())
            .adapterFor(model("model-secret"));

    assertThat(adapter.modelFor(null)).isInstanceOf(OpenAIChatModel.class);
    verify(secrets).decrypt("model-secret", "tenant-1");
  }

  @Test
  void supportsDomesticOpenAiCompatibleProvider() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(
            Optional.of(
                provider("zhipu", "https://open.bigmodel.cn/api/paas/v4", "provider-secret")));
    when(secrets.decrypt("provider-secret", "tenant-1")).thenReturn("server-only-key");

    ModelAdapter adapter =
        new ConfiguredModelAdapterProvider(
                providers, secrets, mock(OAuthCredentialService.class), new ObjectMapper())
            .adapterFor(model(null));

    assertThat(adapter.modelFor(null)).isInstanceOf(OpenAIChatModel.class);
    verify(secrets).decrypt("provider-secret", "tenant-1");
  }

  @Test
  void supportsOriginalOpenAiCompatibleProviderDefaults() {
    for (String providerKey : Set.of("openrouter", "groq", "cerebras", "nebius")) {
      ModelProviderRepository providers = mock(ModelProviderRepository.class);
      SecretService secrets = mock(SecretService.class);
      when(providers.findById("provider-1", "tenant-1"))
          .thenReturn(Optional.of(provider(providerKey, null, "provider-secret")));
      when(secrets.decrypt("provider-secret", "tenant-1")).thenReturn("server-only-key");

      ModelAdapter adapter =
          new ConfiguredModelAdapterProvider(
                  providers, secrets, mock(OAuthCredentialService.class), new ObjectMapper())
              .adapterFor(model(null));

      assertThat(adapter.modelFor(null)).isInstanceOf(OpenAIChatModel.class);
    }
  }

  @Test
  void buildsNativeAnthropicModelWithServerSideSecret() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(Optional.of(provider("anthropic", null, "provider-secret")));
    when(secrets.decrypt("provider-secret", "tenant-1")).thenReturn("server-only-key");

    ModelAdapter adapter =
        new ConfiguredModelAdapterProvider(
                providers, secrets, mock(OAuthCredentialService.class), new ObjectMapper())
            .adapterFor(model(null));

    assertThat(adapter.modelFor(null)).isInstanceOf(AnthropicChatModel.class);
    verify(secrets).decrypt("provider-secret", "tenant-1");
  }

  @Test
  void buildsNativeGeminiModelWithServerSideSecret() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(Optional.of(provider("google", null, "provider-secret")));
    when(secrets.decrypt("provider-secret", "tenant-1")).thenReturn("server-only-key");

    ModelAdapter adapter =
        new ConfiguredModelAdapterProvider(
                providers, secrets, mock(OAuthCredentialService.class), new ObjectMapper())
            .adapterFor(model(null));

    assertThat(adapter.modelFor(null)).isInstanceOf(GeminiChatModel.class);
    verify(secrets).decrypt("provider-secret", "tenant-1");
  }

  @Test
  void rejectsUnknownProviderBeforeDecrypting() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(Optional.of(provider("bedrock", null, "provider-secret")));

    assertThatThrownBy(
            () ->
                new ConfiguredModelAdapterProvider(
                        providers, secrets, mock(OAuthCredentialService.class), new ObjectMapper())
                    .adapterFor(model(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported provider type");
  }

  @Test
  void resolvesGitHubCopilotCredentialFromAuthenticatedUserOAuth() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    OAuthCredentialService oauth = mock(OAuthCredentialService.class);
    Identity identity = new Identity("tenant-1", "user-1", Set.of("ROLE_USER"));
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(Optional.of(provider("github-copilot", "https://api.githubcopilot.com", null)));
    when(oauth.accessToken("github", identity)).thenReturn("oauth-server-only");

    ModelAdapter adapter =
        new ConfiguredModelAdapterProvider(providers, secrets, oauth, new ObjectMapper())
            .adapterFor(model(null), identity);

    assertThat(adapter.modelFor(null)).isInstanceOf(OpenAIChatModel.class);
    verify(oauth).accessToken("github", identity);
  }

  @Test
  void resolvesCodexCredentialIntoResponsesModel() {
    ModelProviderRepository providers = mock(ModelProviderRepository.class);
    SecretService secrets = mock(SecretService.class);
    OAuthCredentialService oauth = mock(OAuthCredentialService.class);
    Identity identity = new Identity("tenant-1", "user-1", Set.of("ROLE_USER"));
    when(providers.findById("provider-1", "tenant-1"))
        .thenReturn(
            Optional.of(provider("openai-codex", "https://chatgpt.com/backend-api/codex", null)));
    when(oauth.accessToken("codex", identity)).thenReturn("oauth-server-only");

    ModelAdapter adapter =
        new ConfiguredModelAdapterProvider(providers, secrets, oauth, new ObjectMapper())
            .adapterFor(model(null), identity);

    assertThat(adapter.modelFor(null)).isInstanceOf(CodexResponsesChatModel.class);
    verify(oauth).accessToken("codex", identity);
  }

  private static Model model(String secretId) {
    Instant now = Instant.now();
    return new Model(
        "model-1",
        "tenant-1",
        null,
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
        null,
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
