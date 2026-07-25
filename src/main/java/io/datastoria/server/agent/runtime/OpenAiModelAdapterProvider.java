package io.datastoria.server.agent.runtime;

import org.springframework.stereotype.Component;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.identity.Identity;
import io.datastoria.server.repository.ModelProviderRepository;
import io.datastoria.server.service.OAuthCredentialService;
import io.datastoria.server.service.SecretService;

/**
 * Production model adapter for OpenAI and OpenAI-compatible chat-completions endpoints.
 *
 * <p>The API key is decrypted only while constructing the provider model and is never accepted
 * from, or returned to, the browser. A model-level secret overrides the provider-level secret.
 */
@Component
public final class OpenAiModelAdapterProvider implements ModelAdapterProvider {

  private final ModelProviderRepository providerRepository;
  private final SecretService secretService;
  private final OAuthCredentialService oauthCredentialService;

  public OpenAiModelAdapterProvider(
      ModelProviderRepository providerRepository,
      SecretService secretService,
      OAuthCredentialService oauthCredentialService) {
    this.providerRepository = providerRepository;
    this.secretService = secretService;
    this.oauthCredentialService = oauthCredentialService;
  }

  @Override
  public ModelAdapter adapterFor(Model modelConfig) {
    return adapterFor(modelConfig, null);
  }

  @Override
  public ModelAdapter adapterFor(Model modelConfig, Identity identity) {
    ModelProvider provider =
        providerRepository
            .findById(modelConfig.providerId(), modelConfig.tenantId())
            .filter(ModelProvider::enabled)
            .orElseThrow(() -> new NotFoundException("ModelProvider", modelConfig.providerId()));
    boolean githubCopilot = "github-copilot".equalsIgnoreCase(provider.providerKey());
    if (!githubCopilot && !isOpenAiCompatible(provider.providerKey())) {
      throw new IllegalArgumentException("Unsupported provider type");
    }
    String apiKey;
    if (githubCopilot) {
      if (identity == null) {
        throw new IllegalStateException("Authenticated identity is required for OAuth provider");
      }
      apiKey = oauthCredentialService.accessToken("github", identity);
    } else {
      String secretId =
          modelConfig.secretId() != null && !modelConfig.secretId().isBlank()
              ? modelConfig.secretId()
              : provider.secretId();
      if (secretId == null || secretId.isBlank()) {
        throw new IllegalStateException("Provider credential is not configured");
      }
      apiKey = secretService.decrypt(secretId, modelConfig.tenantId());
    }
    OpenAIChatModel.Builder builder =
        OpenAIChatModel.builder().apiKey(apiKey).modelName(modelConfig.modelKey()).stream(true);
    if (githubCopilot) {
      builder
          .baseUrl("https://api.githubcopilot.com")
          .endpointPath("/chat/completions")
          .httpTransport(GitHubCopilotHttpTransport.create());
    }
    if (provider.baseUrl() != null && !provider.baseUrl().isBlank()) {
      builder.baseUrl(provider.baseUrl());
    }
    var providerModel = builder.build();
    return ignored -> providerModel;
  }

  private static boolean isOpenAiCompatible(String key) {
    if (key == null) {
      return false;
    }
    String normalized = key.toLowerCase(java.util.Locale.ROOT);
    return normalized.equals("openai")
        || normalized.equals("openai-compatible")
        || normalized.equals("openrouter")
        || normalized.equals("deepseek")
        || normalized.equals("kimi")
        || normalized.equals("moonshot")
        || normalized.equals("xai");
  }
}
