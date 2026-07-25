package io.datastoria.server.agent.runtime;

import org.springframework.stereotype.Component;

import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.datastoria.server.api.error.NotFoundException;
import io.datastoria.server.domain.Model;
import io.datastoria.server.domain.ModelProvider;
import io.datastoria.server.repository.ModelProviderRepository;
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

  public OpenAiModelAdapterProvider(
      ModelProviderRepository providerRepository, SecretService secretService) {
    this.providerRepository = providerRepository;
    this.secretService = secretService;
  }

  @Override
  public ModelAdapter adapterFor(Model modelConfig) {
    ModelProvider provider =
        providerRepository
            .findById(modelConfig.providerId(), modelConfig.tenantId())
            .filter(ModelProvider::enabled)
            .orElseThrow(() -> new NotFoundException("ModelProvider", modelConfig.providerId()));
    if (!isOpenAiCompatible(provider.providerKey())) {
      throw new IllegalArgumentException("Unsupported provider type");
    }
    String secretId =
        modelConfig.secretId() != null && !modelConfig.secretId().isBlank()
            ? modelConfig.secretId()
            : provider.secretId();
    if (secretId == null || secretId.isBlank()) {
      throw new IllegalStateException("Provider credential is not configured");
    }
    String apiKey = secretService.decrypt(secretId, modelConfig.tenantId());
    OpenAIChatModel.Builder builder =
        OpenAIChatModel.builder().apiKey(apiKey).modelName(modelConfig.modelKey()).stream(true);
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
