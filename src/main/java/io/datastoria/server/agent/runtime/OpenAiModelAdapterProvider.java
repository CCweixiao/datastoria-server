package io.datastoria.server.agent.runtime;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

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
  private final ObjectMapper mapper;

  public OpenAiModelAdapterProvider(
      ModelProviderRepository providerRepository,
      SecretService secretService,
      OAuthCredentialService oauthCredentialService,
      ObjectMapper mapper) {
    this.providerRepository = providerRepository;
    this.secretService = secretService;
    this.oauthCredentialService = oauthCredentialService;
    this.mapper = mapper;
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
    boolean openAiCodex = "openai-codex".equalsIgnoreCase(provider.providerKey());
    if (!githubCopilot && !openAiCodex && !isOpenAiCompatible(provider)) {
      throw new IllegalArgumentException("Unsupported provider type");
    }
    if (openAiCodex) {
      if (identity == null) {
        throw new IllegalStateException("Authenticated identity is required for OAuth provider");
      }
      String accessToken = oauthCredentialService.accessToken("codex", identity);
      var providerModel = new CodexResponsesChatModel(modelConfig.modelKey(), accessToken, mapper);
      return ignored -> providerModel;
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

  private static boolean isOpenAiCompatible(ModelProvider provider) {
    String key = provider.providerKey();
    if (key == null || "oauth".equalsIgnoreCase(provider.authType())) {
      return false;
    }
    String normalized = key.toLowerCase(java.util.Locale.ROOT);
    return normalized.equals("openai")
        || normalized.equals("openai-compatible")
        || normalized.equals("openrouter")
        || normalized.equals("deepseek")
        || normalized.equals("kimi")
        || normalized.equals("moonshot")
        || normalized.equals("zhipu")
        || normalized.equals("glm")
        || normalized.equals("minimax")
        || normalized.equals("dashscope")
        || normalized.equals("bailian")
        || normalized.equals("qwen")
        || normalized.equals("aliyun-bailian")
        || normalized.equals("custom")
        || normalized.equals("xai")
        || (!normalized.equals("anthropic")
            && provider.baseUrl() != null
            && !provider.baseUrl().isBlank());
  }
}
